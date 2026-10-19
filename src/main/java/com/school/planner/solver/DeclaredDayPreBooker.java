package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;

/**
 * TASK-564 (27/08 notte, design doc nel task backlog): the DECLARED-DAY PRE-BOOKER,
 * the declared cure of the move-connectivity H34 cells. The construction's round-robin
 * tail is H0-driven (it places leftovers wherever a conflict-free slot exists), so the
 * scarce slots get allocated by ARRIVAL ORDER, not by the sheet's need - a declared
 * (group, date) cell can stay empty while its pool lands on non-declared days. This
 * pass is the quota-driven mirror: for every in-scope declared day still UNDER quota
 * it places the group's still-unplaced lessons on that very date, to quota.
 *
 * <p>Place-ONLY by design: the source is the UNPLACED pool, never a placed lesson -
 * no donor day is touched, no slide, no H17/H31 damage is possible on the sources
 * (the placed-lesson families stay EmptyDayFinisher's territory). Every placement is
 * gate-checked live: H1 (teacher free at the slot), H3 (the group free at the slot),
 * H7 (the teacher's daily cap), VRP (the lesson's own value range: H26/H20/H5), the
 * quota CAP (never above the declared quota), and the border-aware landing shape
 * (an EMPTY day fills from its first calendar-open slot upward, a partial day fills
 * interior gaps then last+1: H17/H31-shaped by construction). Candidates prefer the
 * course's own M32 template cell at the landing slot (faithful to the weekly
 * template), falling back to any course of the group with pool left. Rooms: a free
 * assignable room (tag-matching for labs, untagged preferred for ordinaries), left
 * NULL when none is free (B-ROOM assigns rooms downstream; H29 stays provisional).
 *
 * <p>FLAG {@code planner.construction.prebooking.enabled}, RATIFIED DEFAULT ON
 * (maintainer, 29/08) with a REVISIT-FLAG: adopted on deterministic-seed evidence
 * (-12hard, full-day 91.6, H17 0) while the cross-basin A/B (GRASP seeds 25/26)
 * landed both arms in pre-existing doomed basins and could not confirm; flagged
 * for future reconsideration if cross-basin evidence emerges.
 * The pass is wired INSIDE {@code DayClusteredConstructionPhase.stampWeeklyTemplate}
 * (before the chronological stamp loop: the post-construction venue was measured
 * exhausted, registro 27/08); each candidate placement is gate-checked live (the
 * same gates as the stamp itself) and a cell whose plan cannot reach the quota is
 * declined atomically, never partially applied.
 */
public final class DeclaredDayPreBooker {

    /** TASK-564 flag, standard opt-out form: null/absent -> ON, only the literal "false" disarms. */
    public static final String FLAG = "planner.construction.prebooking.enabled";

    /** Public for ScheduleService's wiring and banner. Standard opt-out form. */
    public static boolean enabled() {
        return !"false".equals(System.getProperty(FLAG));
    }

    /**
     * TASK-590 Phase E prototype, OPT-IN DEFAULT OFF: at a reserve cell whose
     * course died on allowance exhaustion, substitute the first same-date
     * template course that still has non-cannibalizing allowance and passes
     * every gate the original faces (the census's AC#4-passing cells). Only the
     * literal "true" arms it: this is unadopted prototype evidence for
     * TASK-595, never a default behavior.
     */
    public static final String SUBSTITUTION_FLAG =
            "planner.construction.prebooking.substitution.enabled";

    public static boolean substitutionEnabled() {
        return "true".equals(System.getProperty(SUBSTITUTION_FLAG));
    }

    /** The v2 stamp-aware reservation's tail window (declared dates per group,
     *  newest first). Default 56 = the measured risk window (every residual empty
     *  cell of the 26-27/08 cycles sits in the last 8 weeks); fail-soft like the
     *  sibling int knobs. */
    public static final int DEFAULT_TAIL_DAYS = 56;

    /** Public for the construction phase's reserve pass. */
    public static int tailDays() {
        String knob = System.getProperty("planner.construction.prebooking.tail-days");
        if (knob == null) {
            return DEFAULT_TAIL_DAYS;
        }
        try {
            return Math.max(1, Integer.parseInt(knob.trim()));
        } catch (NumberFormatException e) {
            return DEFAULT_TAIL_DAYS;
        }
    }

    /**
     * TASK-564 v2.1 refinement knobs (28/08 notte batch): each cut is armed by
     * default when the main flag is on, and only the literal "false" restores the
     * legacy v2 behavior of that cut - the standard opt-out form, so the
     * differential bench can isolate one cut at a time while the confirmation
     * cycle runs on the refined default.
     */
    public static final String QUOTA_AWARE_KNOB =
            "planner.construction.prebooking.quota-aware.enabled";
    public static final String BLOCK_GUARD_KNOB =
            "planner.construction.prebooking.block-guard.enabled";
    public static final String PARITY_KNOB =
            "planner.construction.prebooking.parity.enabled";

    /** v2.1 cut 2 (quota-aware): surplus-budgeted takes, fill-excluded groups skipped. */
    public static boolean quotaAwareRefinementEnabled() {
        return !"false".equals(System.getProperty(QUOTA_AWARE_KNOB));
    }

    /** v2.1 cut 3 (block guard): a reserved day is a slot-contiguous PREFIX. */
    public static boolean blockGuardRefinementEnabled() {
        return !"false".equals(System.getProperty(BLOCK_GUARD_KNOB));
    }

    /** v2.1 cut 1 (TASK-517 applied to the reserve): no booking denser or more
     *  concentrated than the golden's own practice on the same cell. */
    public static boolean parityRefinementEnabled() {
        return !"false".equals(System.getProperty(PARITY_KNOB));
    }

    /**
     * TASK-564 v2.1 cut 1 data: the golden's own weekday practice for the reserve's
     * two parity caps. PAIR-DAY cap: the MAXIMUM hours/day the golden ever gives one
     * (teacher, group) pair on a weekday - a reserved day may never be denser than
     * the practice's own densest day on that cell. WEEKDAY-share bar: the per-weekday
     * hour distribution of a (group, subject) - a booking is refused on a weekday
     * holding under the 15% "major weekday" bar of the offline check's own
     * definition. (Both are DELIBERATELY per-booking rules, not the offline
     * placement-parity-check's aggregate axes - see the reserve's own comments.)
     * Lazy static,
     * read ONCE per JVM from the classpath golden ({@code seed/lessons.csv}, the same
     * reader family as {@code ConstrainedTeacherAllocator.loadGoldenCoPresenceDepth});
     * golden-runtime read CENSUSED in the TASK-452 ledger sense (see the class javadoc
     * of that allocator): since the 29/08 ratification (flag DEFAULT ON) it rides the
     * PRODUCTION path on every default solve - the debt is live, not
     * exposure-limited. Golden-free degradation is FAIL-OPEN (empty maps, loud warn):
     * the cut goes inert, never kills a solve - the same degradation philosophy as the
     * co-presence depth map, which means a golden retirement silently weakens the
     * parity cap unless the workbook replaces the practice data (TASK-445 vehicle).
     */
    static final class GoldenWeekdayPractice {
        /** "teacher|group|ISO-dow" -> max hours the golden gave the pair on one day. */
        final Map<String, Integer> maxPairDayHours = new HashMap<>();
        /** "group|subject" -> hours by ISO day-of-week (index 1..7). */
        final Map<String, int[]> subjectHoursByWeekday = new HashMap<>();

        Integer maxPairDayHours(String teacher, String group, int isoDow) {
            return maxPairDayHours.get(teacher + "|" + group + "|" + isoDow);
        }

        /** Share of the subject's golden hours the group does on {@code isoDow};
         *  null when the golden never schedules the (group, subject) at all. */
        Double subjectWeekdayShare(String group, String subject, int isoDow) {
            int[] hours = subjectHoursByWeekday.get(group + "|" + subject);
            // hours[0] is the per-pair total (kept by the loader), so the share
            // is a single map get + one division on this hot-ish path
            return hours == null || hours[0] == 0 ? null : (double) hours[isoDow] / hours[0];
        }
    }

    private static volatile GoldenWeekdayPractice goldenPractice;
    private static final GoldenWeekdayPractice EMPTY_PRACTICE = new GoldenWeekdayPractice();

    static GoldenWeekdayPractice goldenWeekdayPractice() {
        GoldenWeekdayPractice p = goldenPractice;
        if (p != null) {
            return p;
        }
        synchronized (GoldenWeekdayPractice.class) {
            if (goldenPractice == null) {
                goldenPractice = loadGoldenWeekdayPractice();
            }
            return goldenPractice;
        }
    }

    private static GoldenWeekdayPractice loadGoldenWeekdayPractice() {
        GoldenWeekdayPractice practice = new GoldenWeekdayPractice();
        List<String[]> rows;
        try {
            // Columns: 0=date 1=day 2=slot 3=closed 4=group 5=teacher 6=subject
            rows = com.school.planner.util.SeedCsv.load("lessons.csv", 7);
        } catch (RuntimeException e) {
            System.out.println("[PreBook-parity] golden not readable (" + e.getMessage()
                    + ") - parity cut INERT (fail-open), bookings unbounded by practice");
            return EMPTY_PRACTICE;
        }
        // PASS 1: hours per (teacher, group, date) - one CSV row = one hour
        Map<String, Integer> pairHoursByDay = new HashMap<>();
        Map<String, int[]> subjHoursByDay = new HashMap<>();   // "group|subject|date" -> [h]
        for (String[] row : rows) {
            if ("True".equalsIgnoreCase(row[3])) {
                continue; // closed rows are not real scheduled hours
            }
            String teacher = row[5];
            String group = row[4];
            String subject = row[6];
            // same protocol as the sanity gate's golden parse and the envelope
            // extractor: Stage and Fasullo hours are excluded (latent-identical
            // for this consumer - Stage keys are never queried - but keeps every
            // golden reader in lockstep instead of one diverging silently)
            if (teacher == null || group == null || subject == null
                    || "Fasullo".equals(teacher) || "Fasullo".equals(subject)
                    || "Stage".equals(teacher) || "Stage".equals(subject)) {
                continue;
            }
            // the runtime queries pass the DB's canonical course.subject: normalize
            // the golden's raw spelling to the same single source (SubjectNormalization
            // is THE map; "RA" is the golden's abbreviation of the G08 real course,
            // renamed at golden-import the same way - otherwise the share bar fails
            // open on exactly the big ordinary subjects the reserve books most)
            subject = com.school.planner.util.SubjectNormalization.normalize(subject);
            if ("RA".equals(subject)) {
                subject = "Recuperi ed Approfondimenti";
            }
            java.time.LocalDate date;
            try {
                date = java.time.LocalDate.parse(row[0].trim());
            } catch (RuntimeException e) {
                continue;
            }
            if (date.getDayOfWeek().getValue() >= 6) {
                continue; // the golden's 117 Saturday hours are protocol-excluded
            }
            pairHoursByDay.merge(teacher + "|" + group + "|" + date, 1, Integer::sum);
            subjHoursByDay.computeIfAbsent(group + "|" + subject + "|" + date, k -> new int[1])[0]++;
        }
        // PASS 2: fold the daily counts into the practice maps. The pair-day MAX
        // is the max over DAILY totals (a per-row merge would degenerate to 1 and
        // starve the reserve - the 28/09 code-review finding that aborted the
        // first v21 launch)
        Map<String, Integer> maxPairDay = new HashMap<>();
        for (var e : pairHoursByDay.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            maxPairDay.merge(parts[0] + "|" + parts[1] + "|"
                    + java.time.LocalDate.parse(parts[2]).getDayOfWeek().getValue(),
                    e.getValue(), Math::max);
        }
        practice.maxPairDayHours.putAll(maxPairDay);
        for (var e : subjHoursByDay.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            int dow = java.time.LocalDate.parse(parts[2]).getDayOfWeek().getValue();
            int[] hours = practice.subjectHoursByWeekday
                    .computeIfAbsent(parts[0] + "|" + parts[1], k -> new int[8]);
            hours[dow] += e.getValue()[0];
            hours[0] += e.getValue()[0]; // slot 0 carries the per-pair TOTAL
        }
        System.out.println("[PreBook-parity] golden weekday practice loaded: "
                + practice.maxPairDayHours.size() + " pair-weekday cells, "
                + practice.subjectHoursByWeekday.size() + " group-subject pairs");
        return practice;
    }

    /**
     * TASK-564 v2.1 measurement journal: every reserved placement as
     * {@code group|date|slot|subject|teacher}, filled by the construction phase's
     * reserve pass ONLY when {@code prebooking.debug} is set (same debug gate as the
     * place-only reject census), drained by the localized bench test into
     * {@code target/reserve-plan.txt} for the offline placement-parity check
     * (TASK-517, the rifinitura-1 deliverable: zero solve cycles). Cleared by the
     * test before and after use; never read by any production path.
     */
    public static final List<String> RESERVE_JOURNAL =
            java.util.Collections.synchronizedList(new ArrayList<>());

    /**
     * TASK-590 LIVE-path rejection census: one {@code group|date|outcome[|detail]}
     * entry per declared tail date the construction phase's reserve walk touches
     * (unlike {@link #RESERVE_JOURNAL}, which is debug-gated and successes-only).
     * Outcome is the first walk-stopping event under the ratified block-guard arm:
     * NEVER_ATTEMPTED, NO_CANDIDATE, ALLOWANCE_EXHAUSTED, VRP_REJECTION (off-grid
     * included), CONFLICT, PARITY_REJECTION; plus BOOKED / BOOKED_PARTIAL /
     * BOOKED_CAP_FULL / FULL_AT_CAP when the date ends without a rejection.
     * Always collected (one line per date, cheap), cleared per construction like
     * the journal, never read by any production path; drained by the localized
     * census bench.
     */
    public static final List<String> RESERVE_CENSUS =
            java.util.Collections.synchronizedList(new ArrayList<>());

    /**
     * TASK-590 AC#4 gate data: at the FIRST allowance exhaustion of a (group, date),
     * the allowance table of EVERY template course on that date
     * ({@code group|date|subject|pool=P|claim=C|allow=A}), so the census report can
     * decide whether a legal alternative course with non-cannibalizing allowance
     * existed (substitution-prototype gate). Same lifecycle as {@link #RESERVE_CENSUS}.
     */
    public static final List<String> ALLOWANCE_SNAPSHOT =
            java.util.Collections.synchronizedList(new ArrayList<>());

    private DeclaredDayPreBooker() {
    }

    /**
     * {@code cellsClosed} = under-quota declared days brought to quota;
     * {@code lessonsPlaced} = previously-unplaced lessons landed (the pool consumed);
     * {@code cellsDeclined} = days left AS FOUND (the plan could not reach the
     * quota: an EMPTY day stays empty - a partial fill would trade the flat H34
     * for worse H32 quota-hours, QFR v2 gate (c) - and an already-partial day
     * keeps its current fill);
     * {@code conflictsAdded} must stay 0. Rooms are deliberately NOT assigned:
     * construction is room-null by design (TASK-446 Alt-B) and phase 4 owns them.
     */
    public record Result(int cellsClosed, int lessonsPlaced, int cellsDeclined,
                         long conflictsAdded) {

        public static Result noop() {
            return new Result(0, 0, 0, 0);
        }
    }

    private record Target(Long groupId, String groupName, LocalDate date, int quota, int deficit) {
    }

    static final Map<String, Integer> PREBOOK_REJECTS = new HashMap<>();

    /** The debug gate both the place-only reject census and the v2.1 reserve
     * journal consult (package-visible: the construction phase journals with it). */
    static final String DEBUG_FLAG = "prebooking.debug";

    private static void census(String key) {
        if (Boolean.getBoolean(DEBUG_FLAG)) {
            PREBOOK_REJECTS.merge(key, 1, Integer::sum);
        }
    }

    public static Result prebook(SchoolSchedule schedule,
                                 BiConsumer<Lesson, Timeslot> moveTimeslot) {
        if (schedule == null || !enabled()) {
            return Result.noop();
        }
        long conflictsBefore = StudentDayCompaction.countConflicts(schedule);
        Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot =
                StudentDayCompaction.buildTimeslotsByDateAndSlot(schedule.getTimeslots());

        // live indexes, updated per placement. groupHours counts NON-STAGE hours
        // only (H32's own population: pseudo-teacher Stage lessons never count
        // against an aula+lab quota); groupSlots still occupies the group's grid
        // (H3 and the landing shape see Stage lessons like any other).
        Map<Long, Set<Long>> teachersByTs = new HashMap<>();
        Map<Long, Map<LocalDate, Set<Integer>>> groupSlots = new HashMap<>();
        Map<Long, Map<LocalDate, Integer>> teacherDayHours = new HashMap<>();
        Map<Long, List<Lesson>> poolByGroup = new HashMap<>(); // unplaced, non-Stage, real
        Map<Long, Map<LocalDate, Integer>> groupHours = new HashMap<>();
        // H28 guard data: per (group, date, courseId) the [min..max] slot span the
        // course already occupies that date, so a candidate can be refused when its
        // landing slot would not extend/adjoin the span (a split block is H28, a
        // timeslot-tier hard the improve-or-noop oracle would blame on the WHOLE pass)
        Map<String, int[]> courseSpanByGroupDate = new HashMap<>();
        for (Lesson l : schedule.getLessons()) {
            var g = l.getStudentGroup();
            var t = l.getTeacher();
            boolean realGroup = g != null && g.id != null;
            boolean realTeacher = t != null && !t.pseudo;
            if (l.timeslot == null) {
                if (realGroup && realTeacher && !l.pinned && l.course != null) {
                    poolByGroup.computeIfAbsent(g.id, k -> new ArrayList<>()).add(l);
                }
                continue;
            }
            if (realTeacher) {
                teachersByTs.computeIfAbsent(l.timeslot.id, k -> new HashSet<>()).add(t.id);
                teacherDayHours.computeIfAbsent(t.id, k -> new HashMap<>())
                        .merge(l.timeslot.getDate(), 1, Integer::sum);
            }
            if (realGroup) {
                int slot = TimeslotSequence.slotIndexOf(l.timeslot);
                groupSlots.computeIfAbsent(g.id, k -> new HashMap<>())
                        .computeIfAbsent(l.timeslot.getDate(), k -> new HashSet<>())
                        .add(slot);
                if (realTeacher) {
                    groupHours.computeIfAbsent(g.id, k -> new HashMap<>())
                            .merge(l.timeslot.getDate(), 1, Integer::sum);
                }
                if (l.course != null && l.course.id != null) {
                    courseSpanByGroupDate.merge(
                            g.id + "@" + l.timeslot.getDate() + "#" + l.course.id,
                            new int[] {slot, slot},
                            (a, b) -> new int[] {Math.min(a[0], b[0]), Math.max(a[1], b[1])});
                }
            }
        }
        for (List<Lesson> pool : poolByGroup.values()) {
            pool.sort(Comparator.comparingLong(l -> l.id != null ? l.id : Long.MAX_VALUE));
        }

        // targets: in-scope declared days under quota, biggest deficit first
        List<Target> targets = new ArrayList<>();
        if (schedule.getStudentGroups() != null) {
            for (var g : schedule.getStudentGroups()) {
                if (g == null || g.id == null
                        || SchoolScheduleConstraintProvider.isQuotaFillExcluded(g.name)) {
                    continue;
                }
                for (var row : SchoolSchedule.getExpectedHoursForGroup(g.id).entrySet()) {
                    int quota = row.getValue().aulaHours + row.getValue().labHours;
                    // H32's scope is dates WITH timeslots: an off-grid declared row
                    // is not a target (and must not pollute cellsDeclined)
                    if (quota <= 0 || !tsByDateSlot.containsKey(row.getKey())) {
                        continue;
                    }
                    int hours = groupHours.getOrDefault(g.id, Map.of())
                            .getOrDefault(row.getKey(), 0);
                    if (hours < quota) {
                        targets.add(new Target(g.id, g.name, row.getKey(), quota, quota - hours));
                    }
                }
            }
        }
        targets.sort(Comparator.comparingInt((Target t) -> t.deficit).reversed()
                .thenComparing(t -> t.groupName).thenComparing(t -> t.date));

        int cellsClosed = 0;
        int cellsDeclined = 0;
        int placedTotal = 0;
        for (Target target : targets) {
            // ATOMIC per cell (QFR v2's gate (c) shape): a PARTIALLY filled declared
            // day is strictly worse than an empty one (H32 charges the missing
            // quota-hours while H34 forgives one flat unit), so the cell is planned
            // in simulation and applied only when it reaches its quota. Planned
            // lessons are RESERVED (not removed from the pool) until the commit: a
            // declined cell must not eat the pool a later cell of the same group
            // could close with.
            record Planned(Lesson lesson, Timeslot ts) {
            }
            List<Planned> plan = new ArrayList<>();
            Set<Lesson> reserved = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            // defensive copy: the simulation must not leak phantom occupancy into
            // the live index when the cell is declined
            Set<Integer> simulatedSlots = new HashSet<>(groupSlots
                    .getOrDefault(target.groupId, Map.of())
                    .getOrDefault(target.date, Set.of()));
            Map<Long, Integer> simulatedTeacherDay = new HashMap<>();
            // H1 and rooms are per-TIMESLOT facts: the simulation mirrors that
            // granularity (two placements of the same cell at different slots may
            // share teacher-class legality and the same room)
            Map<Long, Set<Long>> simulatedTeachersByTs = new HashMap<>();
            Map<String, int[]> simulatedSpans = new HashMap<>();
            int deficit = target.deficit;
            while (deficit > 0) {
                Timeslot ts = nextLandingSlot(schedule, target, simulatedSlots, tsByDateSlot);
                if (ts == null) {
                    census("SLOTS-EXHAUSTED");
                    break;
                }
                int slot = TimeslotSequence.slotIndexOf(ts);
                Lesson chosen = chooseCandidate(poolByGroup.getOrDefault(target.groupId, List.of()),
                        target, slot, ts, reserved, teachersByTs, simulatedTeachersByTs,
                        teacherDayHours, simulatedTeacherDay,
                        courseSpanByGroupDate, simulatedSpans);
                if (chosen == null) {
                    census("NO-CANDIDATE");
                    break;
                }
                var t = chosen.getTeacher();
                plan.add(new Planned(chosen, ts));
                reserved.add(chosen);
                simulatedSlots.add(slot);
                simulatedTeachersByTs.computeIfAbsent(ts.id, k -> new HashSet<>()).add(t.id);
                simulatedTeacherDay.merge(t.id, 1, Integer::sum);
                if (chosen.course.id != null) {
                    simulatedSpans.merge(target.groupId + "@" + target.date + "#" + chosen.course.id,
                            new int[] {slot, slot},
                            (a, b) -> new int[] {Math.min(a[0], b[0]), Math.max(a[1], b[1])});
                }
                deficit--;
            }
            if (deficit > 0) {
                census("CELL-DECLINED");
                cellsDeclined++;
                continue; // leave the cell EMPTY: one flat H34 beats H32 quota-hours
            }
            for (Planned p : plan) {
                removeFromPool(poolByGroup, target.groupId, p.lesson());
                moveTimeslot.accept(p.lesson(), p.ts());
                var t = p.lesson().getTeacher();
                teachersByTs.computeIfAbsent(p.ts().id, k -> new HashSet<>()).add(t.id);
                teacherDayHours.computeIfAbsent(t.id, k -> new HashMap<>())
                        .merge(target.date, 1, Integer::sum);
                placedTotal++;
            }
            groupSlots.computeIfAbsent(target.groupId, k -> new HashMap<>())
                    .computeIfAbsent(target.date, k -> new HashSet<>()).addAll(simulatedSlots);
            cellsClosed++;
        }
        long conflictsAfter = StudentDayCompaction.countConflicts(schedule);
        if (Boolean.getBoolean(DEBUG_FLAG) && !PREBOOK_REJECTS.isEmpty()) {
            System.out.println("[PREBOOK-debug] reject census: " + PREBOOK_REJECTS);
            PREBOOK_REJECTS.clear();
        }
        return new Result(cellsClosed, placedTotal, cellsDeclined,
                conflictsAfter - conflictsBefore);
    }

    /** The border-aware next landing slot: EMPTY day -> the group's first
     *  calendar-open slot on the grid (H31: a formerly-empty day never starts
     *  late); PARTIAL day -> first interior gap, then last+1 (H17: never a
     *  slot above last+1, never before the day's own start). Null when the
     *  day is full to its grid. */
    private static Timeslot nextLandingSlot(SchoolSchedule schedule, Target target,
                                            Set<Integer> occupied,
                                            Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot) {
        Map<Integer, Timeslot> slots = tsByDateSlot.get(target.date);
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        int firstOpen;
        if (occupied.isEmpty()) {
            firstOpen = windowFirstOpenSlot(schedule, target, slots);
        } else {
            firstOpen = -1;
            int last = occupied.stream().max(Integer::compareTo).orElse(0);
            int first = occupied.stream().min(Integer::compareTo).orElse(0);
            for (int s = first; s < last; s++) {
                if (!occupied.contains(s) && slots.containsKey(s)) {
                    firstOpen = s;
                    break;
                }
            }
            if (firstOpen < 0) {
                firstOpen = last + 1;
            }
        }
        return slots.get(firstOpen); // null when the slot does not exist on the grid
    }

    /** The day's first slot that exists on the grid AND is open in the group's
     *  calendar window: the shared {@link StudentDayCompaction#firstCalendarOpenSlot}
     * (single source with EmptyDayFinisher's gate (d)). */
    private static int windowFirstOpenSlot(SchoolSchedule schedule, Target target,
                                           Map<Integer, Timeslot> slots) {
        return StudentDayCompaction.firstCalendarOpenSlot(target.groupId, target.date, slots);
    }

    /** Template-faithful candidate choice: among the group's pool, lessons whose
     *  course's M32 template contains the landing cell first, then the rest;
     *  within a class by stable id. Each candidate must pass H1 (teacher free,
     *  global + this cell's simulation), H7 (the teacher's daily cap via the
     *  single-source accessor, simulation included), H28 (the landing slot must
     *  fall inside, extend or adjoin the course's existing same-date span -
     *  filling a hole inside one's own block is not a split; the oracle
     *  backstops the cross-teacher corner), and VRP
     *  (the lesson's own range). {@code reserved} holds this cell's planned
     *  lessons (not yet committed). Returns null when nobody qualifies. */
    private static Lesson chooseCandidate(List<Lesson> pool, Target target, int slot, Timeslot ts,
                                          Set<Lesson> reserved,
                                          Map<Long, Set<Long>> teachersByTs,
                                          Map<Long, Set<Long>> simulatedTeachersByTs,
                                          Map<Long, Map<LocalDate, Integer>> teacherDayHours,
                                          Map<Long, Integer> simulatedTeacherDay,
                                          Map<String, int[]> courseSpanByGroupDate,
                                          Map<String, int[]> simulatedSpans) {
        Lesson fallback = null;
        for (Lesson l : pool) {
            if (reserved.contains(l)) {
                continue;
            }
            var t = l.getTeacher();
            if (teachersByTs.getOrDefault(ts.id, Set.of()).contains(t.id)
                    || simulatedTeachersByTs.getOrDefault(ts.id, Set.of()).contains(t.id)) {
                continue; // H1, global + this cell's simulation
            }
            int dayHours = teacherDayHours.getOrDefault(t.id, Map.of())
                    .getOrDefault(target.date, 0)
                    + simulatedTeacherDay.getOrDefault(t.id, 0);
            if (dayHours + 1 > t.getEffectiveMaxDailyHours()) {
                continue; // H7
            }
            if (l.course.id != null) {
                String spanKey = target.groupId + "@" + target.date + "#" + l.course.id;
                int[] span = simulatedSpans.getOrDefault(spanKey,
                        courseSpanByGroupDate.get(spanKey));
                if (span != null && slot != span[0] - 1 && slot != span[1] + 1
                        && (slot < span[0] || slot > span[1])) {
                    continue; // H28: the landing would split the course's same-date block
                }
            }
            // TASK-578: per-slot legality: the worst Fresh multiplicity of the audit
            // (~3x10^5 rebuilds per pass here: 600 cells x the whole unplaced pool)
            if (!l.isSlotLegalFresh(ts)) {
                continue; // VRP (H26/H20/H5)
            }
            if (fallback == null) {
                fallback = l;
            }
            if (SchoolSchedule.isOnTemplateCell(l.course.id, ts)) {
                return l; // template-faithful: take immediately
            }
        }
        return fallback;
    }

    private static void removeFromPool(Map<Long, List<Lesson>> poolByGroup, Long groupId, Lesson l) {
        poolByGroup.getOrDefault(groupId, new ArrayList<>()).remove(l);
    }

}
