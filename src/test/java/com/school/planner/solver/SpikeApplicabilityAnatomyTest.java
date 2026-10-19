package com.school.planner.solver;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.school.planner.bootstrap.DataGenerator;
import com.school.planner.bootstrap.SnapshotFixture;
import com.school.planner.domain.DailyActivityQuota;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;
import com.school.planner.service.GroupCalendarService;

/**
 * <b>THROWAWAY SPIKE (usa-e-getta), 2026-07-27 - delete once the two verdicts are recorded.</b>
 * Diagnostic printer only ({@code assertTrue(true)}), no production code touched, not part of
 * any Definition of Done. It exists to answer two applicability questions with the REAL solver
 * gates rather than a Python proxy, following {@link Task419H17GapAnatomyTest}'s pattern (same
 * package, so the package-private primitives of {@link BoxRepair} / {@link HoleRepair} /
 * {@link StudentDayCompaction} / {@link SchoolScheduleConstraintProvider} are callable
 * directly instead of being re-implemented as twins).
 *
 * <p><b>SPIKE 1 - AnchorRepair feasibility (day-start).</b> For every (group, date) whose first
 * occupied slot is later than the group's first CALENDAR-OPEN slot ({@link
 * SchoolScheduleConstraintProvider#m18DayStartOffset}, M18's own semantics), can the leading
 * hole(s) be closed by a HoleRepair-style IMPORT (a same-group unpinned lesson from ANOTHER
 * date, no slide) through the identical gate chain - conflict-free, {@link
 * BoxRepair#passesPlacementGates}, {@link BoxRepair#isDepartureSafe}, VRP membership last?
 * A day starting at offset k needs ALL k leading slots filled with DISTINCT movers, so the
 * greedy fills them adjacent-first (slot minSlot-1 downwards): filling the far one first would
 * open an H17 break and be refused by the arrival gate, which is the correct modelling of the
 * joint requirement. Every closure is priced on the score's own curves (M32 via
 * {@link StagedPinPolicy#deviationWeightOf}, M20 via the shared marginal helpers, M15 via the
 * subject-slice break width) so the verdict is "closable AND at what medium cost".
 *
 * <p><b>SPIKE 2 - G04 empty-in-span days.</b> The dates inside G04's own span that carry a
 * positive expected-hours fact yet hold zero lessons: what the fact row says, which subjects the
 * real {@link DailyActivityQuota#permits} decision allows there, and whether legal donors would
 * exist if H24 (the aula-quota half) were relaxed to mirror golden practice.
 *
 * <p><b>Order note:</b> SPIKE 2 is measured FIRST because SPIKE 1 commits its greedy imports to
 * the in-memory grid (via {@link HoleRepair#commitCrossDayMove}, the production commit, so every
 * live index stays honest exactly as in a real pass). SPIKE 2 therefore always reads the pristine
 * snapshot.
 *
 * <p>Invoke: {@code PLANNER_TEST_SPIKE_APPLICABILITY=<snapshot dir> ./scripts/core/test.sh -c SpikeApplicabilityAnatomyTest}
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_SPIKE_APPLICABILITY", matches = ".+")
class SpikeApplicabilityAnatomyTest {

    /** Whole-school ceremonial first school day: the golden itself starts 13 groups late here. */
    private static final LocalDate FIRST_SCHOOL_DAY = LocalDate.of(2026, 9, 14);

    /** M15's weigher: {@code ofMedium(20)} x gap MINUTES; one slot width = 60 minutes. */
    private static final int M15_MEDIUM_PER_SLOT_WIDTH = 20 * 60;

    @Inject AvailabilityExpansionService availabilityExpansionService;
    @Inject DataGenerator dataGenerator;
    @Inject jakarta.persistence.EntityManager em;

    @BeforeEach
    @AfterEach
    @Transactional
    void clean() {
        SnapshotFixture.clearTables(em);
        SnapshotFixture.resetStatics();
    }

    @Test
    @Transactional
    void printApplicabilityAnatomy() throws Exception {
        Path dir = SnapshotFixture.resolveSnapshotDir(System.getenv("PLANNER_TEST_SPIKE_APPLICABILITY"));
        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadSnapshotNoClean(dir, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);

        List<Lesson> lessons = snap.lessons;
        System.out.println("=== SPIKE applicability anatomy: " + dir + " ===");
        long placed = lessons.stream().filter(l -> l.timeslot != null).count();
        System.out.printf("  population: %d lessons (%d placed, %d boxed), skippedClosedDateRows=%d%n%n",
                lessons.size(), placed, lessons.size() - placed, snap.skippedClosedDateRows);

        // The live production indexes (all built by the real primitives).
        Map<Timeslot, List<Lesson>> byTs = StudentDayCompaction.buildOccupancyIndex(lessons);
        Map<String, TreeMap<Integer, Boolean>> groupDaySlots = BoxRepair.buildGroupDaySlots(lessons);
        Map<String, Integer> teacherDaily = BoxRepair.buildTeacherDaily(lessons);
        Map<String, TreeMap<Integer, Lesson>> dayLessons = HoleRepair.buildDayLessons(lessons);
        Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot =
                StudentDayCompaction.buildTimeslotsByDateAndSlot(snap.timeslots);

        spike2FCEmptyDays(snap, lessons, byTs, groupDaySlots, teacherDaily, dayLessons, tsByDateSlot);
        spike1AnchorRepair(lessons, byTs, groupDaySlots, teacherDaily, dayLessons, tsByDateSlot);

        org.junit.jupiter.api.Assertions.assertTrue(true);
    }

    // ══════════════════════════ SPIKE 1: AnchorRepair feasibility ══════════════════════════

    private record LateDay(String key, StudentGroup group, LocalDate date, int minOpen, int minSlot, int offset) {
    }

    private void spike1AnchorRepair(List<Lesson> lessons,
                                    Map<Timeslot, List<Lesson>> byTs,
                                    Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                    Map<String, Integer> teacherDaily,
                                    Map<String, TreeMap<Integer, Lesson>> dayLessons,
                                    Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot) {
        boolean m18Guard = System.getenv("PLANNER_TEST_SPIKE_M18_GUARD") != null;
        System.out.println("\n\n══════════ SPIKE 1 - AnchorRepair feasibility (day-start) ══════════");
        System.out.printf("  donor M18-departure guard (SurplusRebalance's keepsDayAnchored rule): %s%n",
                m18Guard ? "ON" : "OFF (naive HoleRepair-style donor pool)");

        List<LateDay> lateDays = new ArrayList<>();
        int legitimateFirstDay = 0;
        int closedSentinelDays = 0;
        for (Map.Entry<String, TreeMap<Integer, Lesson>> entry : dayLessons.entrySet()) {
            TreeMap<Integer, Lesson> bySlot = entry.getValue();
            if (bySlot.isEmpty()) continue;
            Lesson any = bySlot.firstEntry().getValue();
            StudentGroup group = any.getStudentGroup();
            LocalDate date = any.timeslot.getDate();
            int[] window = SchoolSchedule.getGroupCalendarWindow(group.id, date.getDayOfWeek(), date);
            if (window[1] < 0) {
                closedSentinelDays++;
                continue;   // closed-day sentinel: M18 does not penalize it (H20's business)
            }
            int minSlot = bySlot.firstKey();
            int offset = SchoolScheduleConstraintProvider.m18DayStartOffset(group, date, minSlot);
            if (offset <= 0) continue;
            if (FIRST_SCHOOL_DAY.equals(date)) {
                legitimateFirstDay++;
                continue;
            }
            lateDays.add(new LateDay(entry.getKey(), group, date, window[1], minSlot, offset));
        }
        lateDays.sort(Comparator.comparing((LateDay d) -> d.group().name).thenComparing(LateDay::date));

        int totalOffsetSlots = lateDays.stream().mapToInt(LateDay::offset).sum();
        System.out.printf("  M18-late (group, date) days (offset > 0 vs first calendar-open slot): %d%n", lateDays.size());
        System.out.printf("    excluded as golden-legitimate (%s, ceremonial first school day): %d%n",
                FIRST_SCHOOL_DAY, legitimateFirstDay);
        System.out.printf("    days on a closed-day calendar sentinel (M18 exempt): %d%n", closedSentinelDays);
        System.out.printf("    total leading hole slots to fill: %d  (M18 medium on the table: %d)%n",
                totalOffsetSlots, 50 * totalOffsetSlots);
        Map<String, Integer> byGroup = new TreeMap<>();
        Map<Integer, Integer> byOffset = new TreeMap<>();
        for (LateDay d : lateDays) {
            byGroup.merge(d.group().name, 1, Integer::sum);
            byOffset.merge(d.offset(), 1, Integer::sum);
        }
        System.out.println("    per group: " + byGroup);
        System.out.println("    per offset (1 leading slot vs 2+): " + byOffset);
        System.out.println();

        // ── greedy closure, adjacent-slot-first, distinct movers, live indexes ──
        int fullyClosed = 0;
        int partiallyClosed = 0;
        int notClosed = 0;
        int slotsFilled = 0;
        long m32Bill = 0;
        long m20RemovalBill = 0;
        long m20FillGain = 0;
        long m15Bill = 0;
        int donorDaysEmptied = 0;
        int donorDaysNewlyLate = 0;
        Map<String, Integer> blockerTally = new TreeMap<>();
        List<String> unclosableLines = new ArrayList<>();

        for (LateDay day : lateDays) {
            TreeMap<Integer, Lesson> bySlot = dayLessons.get(day.key());
            StringBuilder trace = new StringBuilder();
            int filled = 0;
            String stopReason = null;
            for (int s = day.minSlot() - 1; s >= day.minOpen(); s--) {
                Timeslot target = tsByDateSlot.getOrDefault(day.date(), Map.of()).get(s);
                if (target == null) {
                    stopReason = "NO-TIMESLOT-ON-GRID";
                    break;
                }
                Lesson pick = findAnchorImport(lessons, day.group(), day.date(), target,
                        byTs, groupDaySlots, teacherDaily, m18Guard);
                if (pick == null) {
                    stopReason = classifyBlockers(lessons, day.group(), day.date(), target,
                            byTs, groupDaySlots, teacherDaily, blockerTally, m18Guard);
                    break;
                }
                // ── price the move BEFORE committing (every term read off the live maps) ──
                LocalDate originDate = pick.timeslot.getDate();
                String originKey = day.group().id + "@" + originDate;
                TreeMap<Integer, Boolean> originSlots = groupDaySlots.get(originKey);
                TreeMap<Integer, Boolean> targetSlots = groupDaySlots.get(day.key());
                int m32 = SchoolSchedule.isOnTemplateCell(pick) ? StagedPinPolicy.deviationWeightOf(pick) : 0;
                int m20Rem = SchoolScheduleConstraintProvider.m20MarginalRemovalCost(day.group(), originDate,
                        BoxRepair.m20Occupancy(originSlots), originSlots == null ? 0 : originSlots.size());
                int m20Gain = SchoolScheduleConstraintProvider.m20MarginalFillGain(day.group(), day.date(),
                        BoxRepair.m20Occupancy(targetSlots), targetSlots == null ? 0 : targetSlots.size());
                int m15 = m15DeltaSlots(pick, dayLessons.get(originKey), dayLessons.get(day.key()), s)
                        * M15_MEDIUM_PER_SLOT_WIDTH;
                TreeMap<Integer, Lesson> originDay = dayLessons.get(originKey);
                int originMinBefore = originDay.firstKey();
                int originSizeBefore = originDay.size();

                HoleRepair.commitCrossDayMove(pick, target, byTs, groupDaySlots, teacherDaily, dayLessons,
                        (l, ts) -> l.timeslot = ts, l -> l.room = null);

                filled++;
                slotsFilled++;
                m32Bill += m32;
                m20RemovalBill += m20Rem;
                m20FillGain += m20Gain;
                m15Bill += m15;
                if (originSizeBefore == 1) {
                    donorDaysEmptied++;
                } else if (originDay.firstKey() > originMinBefore
                        && SchoolScheduleConstraintProvider.m18DayStartOffset(day.group(), originDate,
                        originDay.firstKey()) > 0
                        && SchoolScheduleConstraintProvider.m18DayStartOffset(day.group(), originDate,
                        originMinBefore) == 0) {
                    donorDaysNewlyLate++;
                }
                trace.append(String.format("s%d<-%s/%s@%s%s[M32 %d, M20rem %d, M20gain %d, M15 %d] ",
                        s, abbrev(pick.getSubject()), teacherOf(pick), originDate,
                        SchoolSchedule.isOnTemplateCell(pick) ? "(T)" : "(free)",
                        m32, m20Rem, m20Gain, m15));
            }
            if (filled == day.offset()) {
                fullyClosed++;
            } else if (filled > 0) {
                partiallyClosed++;
            } else {
                notClosed++;
            }
            String verdict = filled == day.offset() ? "ANCHORED"
                    : (filled > 0 ? "PARTIAL " + filled + "/" + day.offset() : "UNCLOSABLE");
            System.out.printf("  %-8s %s  open s%d, starts s%d (offset %d) : %-16s %s%s%n",
                    day.group().name, day.date(), day.minOpen(), day.minSlot(), day.offset(), verdict,
                    trace, stopReason == null ? "" : " [stop: " + stopReason + "]");
            if (filled < day.offset()) {
                unclosableLines.add(day.group().name + " " + day.date() + " offset " + day.offset()
                        + " filled " + filled + " stop=" + stopReason);
            }
        }

        System.out.println("\n  ── SPIKE 1 closability table ──");
        System.out.printf("    late days total                : %d%n", lateDays.size());
        System.out.printf("    fully closable by import       : %d (%.1f%%)%n",
                fullyClosed, lateDays.isEmpty() ? 0.0 : 100.0 * fullyClosed / lateDays.size());
        System.out.printf("    partially closable             : %d%n", partiallyClosed);
        System.out.printf("    not closable at all            : %d%n", notClosed);
        System.out.printf("    leading slots filled           : %d of %d%n", slotsFilled, totalOffsetSlots);
        System.out.println("    blocking gate of the first unfillable slot (tally): " + blockerTally);
        System.out.println("\n  ── SPIKE 1 medium collateral of the greedy closure set ──");
        System.out.printf("    M32 (template deviation) bill  : %d medium%n", m32Bill);
        System.out.printf("    M20 donor-day removal cost     : %d medium%n", m20RemovalBill);
        System.out.printf("    M20 target-day fill GAIN       : %d medium (credit)%n", m20FillGain);
        System.out.printf("    M15 (subject split) bill       : %d medium%n", m15Bill);
        System.out.printf("    M18 saved (50 x offset)        : %d medium (credit)%n", 50 * slotsFilled);
        System.out.printf("    NET medium (bills - credits)   : %d%n",
                m32Bill + m20RemovalBill + m15Bill - m20FillGain - 50L * slotsFilled);
        System.out.printf("    donor days EMPTIED by a move   : %d%n", donorDaysEmptied);
        System.out.printf("    donor days made newly M18-late : %d%n", donorDaysNewlyLate);
        if (!unclosableLines.isEmpty()) {
            System.out.println("\n  ── residual (not fully anchored) ──");
            unclosableLines.forEach(l -> System.out.println("    " + l));
        }

        // ── re-measure M18 on the post-greedy grid (the honest "after" number) ──
        int lateAfter = 0;
        int offsetAfter = 0;
        for (Map.Entry<String, TreeMap<Integer, Lesson>> entry : dayLessons.entrySet()) {
            TreeMap<Integer, Lesson> bySlot = entry.getValue();
            if (bySlot.isEmpty()) continue;
            Lesson any = bySlot.firstEntry().getValue();
            StudentGroup group = any.getStudentGroup();
            LocalDate date = any.timeslot.getDate();
            if (SchoolSchedule.getGroupCalendarWindow(group.id, date.getDayOfWeek(), date)[1] < 0) continue;
            int off = SchoolScheduleConstraintProvider.m18DayStartOffset(group, date, bySlot.firstKey());
            if (off > 0 && !FIRST_SCHOOL_DAY.equals(date)) {
                lateAfter++;
                offsetAfter += off;
            }
        }
        System.out.printf("%n  post-greedy re-measure: %d late days (%d offset slots) vs %d (%d) before%n",
                lateAfter, offsetAfter, lateDays.size(), totalOffsetSlots);
        System.out.printf("  H17 penalized hole slots after the greedy: %d%n",
                HoleRepair.countPenalizedGapSlots(lessons));
    }

    /**
     * The anchor-import candidate: mirrors {@link HoleRepair}'s own {@code findImport} - same
     * gate chain in the same order (conflict-free, {@link BoxRepair#passesPlacementGates},
     * {@link BoxRepair#isDepartureSafe}, VRP membership LAST), same two-level preference (free
     * tail before on-template) and the same cost key (M32 weight + M20 source-day removal).
     * It cannot call {@code findImport} itself, which is private.
     */
    private static Lesson findAnchorImport(List<Lesson> lessons, StudentGroup group, LocalDate holeDate,
                                           Timeslot target, Map<Timeslot, List<Lesson>> byTs,
                                           Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                           Map<String, Integer> teacherDaily, boolean m18Guard) {
        for (boolean onTemplate : new boolean[] { false, true }) {
            Lesson best = null;
            int bestCost = Integer.MAX_VALUE;
            for (Lesson lesson : lessons) {
                if (lesson.pinned || lesson.timeslot == null) continue;
                StudentGroup g = lesson.getStudentGroup();
                if (g == null || !g.id.equals(group.id)) continue;
                if (holeDate.equals(lesson.timeslot.getDate())) continue;   // import only, never a slide
                if (SchoolSchedule.isOnTemplateCell(lesson) != onTemplate) continue;
                if (!StudentDayCompaction.isSlotConflictFree(lesson, target, byTs)) continue;
                if (!BoxRepair.passesPlacementGates(lesson, target, groupDaySlots, teacherDaily)) continue;
                if (!BoxRepair.isDepartureSafe(lesson, groupDaySlots)) continue;
                if (m18Guard && !keepsDonorAnchored(lesson, groupDaySlots)) continue;
                if (!lesson.getAvailableTimeslots().contains(target)) continue;
                TreeMap<Integer, Boolean> src = groupDaySlots.get(g.id + "@" + lesson.timeslot.getDate());
                int cost = (onTemplate ? StagedPinPolicy.deviationWeightOf(lesson) : 0)
                        + SchoolScheduleConstraintProvider.m20MarginalRemovalCost(g, lesson.timeslot.getDate(),
                        BoxRepair.m20Occupancy(src), src == null ? 0 : src.size());
                if (cost < bestCost) {
                    bestCost = cost;
                    best = lesson;
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /**
     * M18 DEPARTURE guard, the exact rule {@link SurplusRebalance}'s {@code keepsDayAnchored}
     * applies to its own donors: true iff taking {@code lesson} off its day does not RAISE that
     * day's own M18 start offset ({@link SchoolScheduleConstraintProvider#m18DayStartOffset}).
     * Without it an anchor import is a shell game - it fixes one day's start by de-anchoring the
     * donor's, which the OFF run of this spike measures directly. (The production method is
     * private, hence this local mirror; the offset arithmetic itself is delegated to M18's own
     * rule, never re-derived.)
     */
    private static boolean keepsDonorAnchored(Lesson lesson,
                                              Map<String, TreeMap<Integer, Boolean>> groupDaySlots) {
        StudentGroup group = lesson.getStudentGroup();
        LocalDate date = lesson.timeslot.getDate();
        TreeMap<Integer, Boolean> daySlots = groupDaySlots.get(group.id + "@" + date);
        if (daySlots == null || daySlots.isEmpty()) return true;
        int slot = TimeslotSequence.slotIndexOf(lesson.timeslot);
        if (slot != daySlots.firstKey()) return true;      // the day's minimum slot does not move
        Integer nextSlot = daySlots.higherKey(slot);
        if (nextSlot == null) return true;                 // the day empties: M18 has no tuple left
        return SchoolScheduleConstraintProvider.m18DayStartOffset(group, date, nextSlot)
                <= SchoolScheduleConstraintProvider.m18DayStartOffset(group, date, slot);
    }

    /**
     * Why no same-group import reaches {@code target}: tallies, over the whole candidate pool,
     * the FIRST gate each candidate fails (same order as the search above) and returns the
     * dominant reason. Purely diagnostic.
     */
    private static String classifyBlockers(List<Lesson> lessons, StudentGroup group, LocalDate holeDate,
                                           Timeslot target, Map<Timeslot, List<Lesson>> byTs,
                                           Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                           Map<String, Integer> teacherDaily,
                                           Map<String, Integer> globalTally, boolean m18Guard) {
        Map<String, Integer> local = new TreeMap<>();
        int pool = 0;
        for (Lesson lesson : lessons) {
            if (lesson.pinned || lesson.timeslot == null) continue;
            StudentGroup g = lesson.getStudentGroup();
            if (g == null || !g.id.equals(group.id)) continue;
            if (holeDate.equals(lesson.timeslot.getDate())) continue;
            pool++;
            String reason;
            if (!StudentDayCompaction.isSlotConflictFree(lesson, target, byTs)) {
                reason = "teacher-busy-at-slot";
            } else if (!BoxRepair.passesPlacementGates(lesson, target, groupDaySlots, teacherDaily)) {
                reason = "caps-or-H17-arrival";
            } else if (!BoxRepair.isDepartureSafe(lesson, groupDaySlots)) {
                reason = "departure-unsafe";
            } else if (m18Guard && !keepsDonorAnchored(lesson, groupDaySlots)) {
                reason = "would-de-anchor-donor";
            } else if (!lesson.getAvailableTimeslots().contains(target)) {
                reason = "VRP";
            } else {
                reason = "LEGAL(unreachable)";
            }
            local.merge(reason, 1, Integer::sum);
        }
        String dominant = local.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("EMPTY-POOL");
        globalTally.merge(dominant, 1, Integer::sum);
        return dominant + " (pool " + pool + ": " + local + ")";
    }

    /**
     * M15's break-width delta in SLOTS for moving {@code mover} out of {@code originDay} into
     * {@code targetSlot} of {@code targetDay}: the (group, subject) slice break penalty measured
     * by {@link BoxRepair#h17DayPenalty} (the same scanner M15's break shape uses, see
     * SurplusRebalance's M15 section), departure and arrival summed. Lab lessons are outside M15
     * entirely (its own {@code !isLab()} filter) and score 0.
     */
    private static int m15DeltaSlots(Lesson mover, TreeMap<Integer, Lesson> originDay,
                                     TreeMap<Integer, Lesson> targetDay, int targetSlot) {
        if (mover.isLab()) return 0;
        int delta = 0;
        TreeMap<Integer, Boolean> originSlice = subjectSlice(mover.getSubject(), originDay);
        int originSlot = TimeslotSequence.slotIndexOf(mover.timeslot);
        int before = BoxRepair.h17DayPenalty(originSlice);
        TreeMap<Integer, Boolean> afterRemoval = new TreeMap<>(originSlice);
        afterRemoval.remove(originSlot);
        delta += BoxRepair.h17DayPenalty(afterRemoval) - before;
        TreeMap<Integer, Boolean> targetSlice = subjectSlice(mover.getSubject(), targetDay);
        int tBefore = BoxRepair.h17DayPenalty(targetSlice);
        TreeMap<Integer, Boolean> afterArrival = new TreeMap<>(targetSlice);
        afterArrival.put(targetSlot, false);
        delta += BoxRepair.h17DayPenalty(afterArrival) - tBefore;
        return delta;
    }

    /** The {@code slotIndex -> authoritatively pinned} map of one (group, subject, date) slice. */
    private static TreeMap<Integer, Boolean> subjectSlice(String subject, TreeMap<Integer, Lesson> day) {
        TreeMap<Integer, Boolean> slice = new TreeMap<>();
        if (day == null) return slice;
        for (Map.Entry<Integer, Lesson> entry : day.entrySet()) {
            Lesson other = entry.getValue();
            if (other.isLab() || !Objects.equals(subject, other.getSubject())) continue;
            slice.put(entry.getKey(), BaseConstraintProvider.isAuthoritativePin(other));
        }
        return slice;
    }

    // ══════════════════════════ SPIKE 2: G04 empty-in-span days ══════════════════════════

    private void spike2FCEmptyDays(SnapshotFixture.Snapshot snap, List<Lesson> lessons,
                                    Map<Timeslot, List<Lesson>> byTs,
                                    Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                    Map<String, Integer> teacherDaily,
                                    Map<String, TreeMap<Integer, Lesson>> dayLessons,
                                    Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot) {
        System.out.println("\n\n══════════ SPIKE 2 - G04 empty-in-span days ══════════");
        StudentGroup fc = snap.groups.get("G04");
        if (fc == null) {
            System.out.println("  no G04 group in this fixture - skipped");
            return;
        }

        // G04's own course inventory, split by the real quota-bound decision.
        Map<String, int[]> bySubject = new TreeMap<>();  // subject -> {bound?, lessons, placed}
        Map<String, String> teacherBySubject = new TreeMap<>();
        for (Lesson l : lessons) {
            StudentGroup g = l.getStudentGroup();
            if (g == null || !g.id.equals(fc.id)) continue;
            String subj = l.getSubject();
            int[] agg = bySubject.computeIfAbsent(subj, k -> new int[] {
                    DailyActivityQuota.isQuotaBoundLabSubject("G04", subj) ? 1 : 0, 0, 0 });
            agg[1]++;
            if (l.timeslot != null) agg[2]++;
            teacherBySubject.putIfAbsent(subj, teacherOf(l));
        }
        System.out.println("  G04 subject inventory (bound-lab per QUOTA_BOUND_LAB_SUBJECTS_BY_GROUP):");
        for (Map.Entry<String, int[]> e : bySubject.entrySet()) {
            System.out.printf("    %-42s %-12s %-9s lessons=%d placed=%d%n", e.getKey(),
                    teacherBySubject.get(e.getKey()), e.getValue()[0] == 1 ? "BOUND-LAB" : "non-bound",
                    e.getValue()[1], e.getValue()[2]);
        }

        // Span = first..last date G04 actually occupies in this snapshot.
        TreeSet<LocalDate> occupied = new TreeSet<>();
        for (Lesson l : lessons) {
            StudentGroup g = l.getStudentGroup();
            if (g != null && g.id.equals(fc.id) && l.timeslot != null) occupied.add(l.timeslot.getDate());
        }
        if (occupied.isEmpty()) {
            System.out.println("  G04 has no placed lesson - skipped");
            return;
        }
        Set<LocalDate> gridDates = new HashSet<>();
        for (Timeslot ts : snap.timeslots) gridDates.add(ts.getDate());
        List<LocalDate> spanDates = new ArrayList<>(new TreeSet<>(gridDates)).stream()
                .filter(d -> !d.isBefore(occupied.first()) && !d.isAfter(occupied.last()))
                .sorted().toList();

        System.out.printf("%n  span: %s .. %s (%d school dates on the grid, %d occupied by G04)%n",
                occupied.first(), occupied.last(), spanDates.size(), occupied.size());

        List<LocalDate> emptyExpected = new ArrayList<>();
        for (LocalDate date : spanDates) {
            if (occupied.contains(date)) continue;
            DailyActivityQuota row = SchoolSchedule.getExpectedHoursRow(fc.id, date);
            if (row == null || row.aulaHours + row.labHours <= 0) continue;
            emptyExpected.add(date);
        }
        System.out.printf("  EMPTY-IN-SPAN days with expected (aula+lab) > 0: %d%n%n", emptyExpected.size());

        for (LocalDate date : emptyExpected) {
            DailyActivityQuota row = SchoolSchedule.getExpectedHoursRow(fc.id, date);
            int[] window = SchoolSchedule.getGroupCalendarWindow(fc.id, date.getDayOfWeek(), date);
            System.out.printf("  ── %s (%s): fact aula=%d lab=%d stage=%d | m20Quota=%d effectiveQuota=%d | "
                            + "calendar open %d slots [%d..%d] | M20.2 cost if left empty=%d%n",
                    date, date.getDayOfWeek(), row.aulaHours, row.labHours, row.stageHours,
                    SchoolScheduleConstraintProvider.m20Quota(fc, date),
                    SchoolScheduleConstraintProvider.effectiveQuota(fc, date),
                    window[0], window[1], window[2],
                    SchoolScheduleConstraintProvider.m20DayCost(fc, date, 0, 0));
            System.out.printf("       permits(bound-lab)=%s  permits(non-bound/aula)=%s  -> legal subjects TODAY: %s%n",
                    DailyActivityQuota.permits(row, true), DailyActivityQuota.permits(row, false),
                    legalSubjectsToday(bySubject, row));

            // Under the REAL rules: does any donor exist at all?
            System.out.printf("       real-rule donors (any slot, full gate chain)   : %s%n",
                    donorReport(lessons, fc, date, tsByDateSlot, byTs, groupDaySlots, teacherDaily, false));
            // Under a RELAXED H24 (non-bound subjects allowed even at aulaHours == 0), mirroring golden.
            System.out.printf("       relaxed-H24 donors (aula filter bypassed only) : %s%n",
                    donorReport(lessons, fc, date, tsByDateSlot, byTs, groupDaySlots, teacherDaily, true));
        }
    }

    private static String legalSubjectsToday(Map<String, int[]> bySubject, DailyActivityQuota row) {
        List<String> legal = new ArrayList<>();
        for (Map.Entry<String, int[]> e : bySubject.entrySet()) {
            if (DailyActivityQuota.permits(row, e.getValue()[0] == 1)) legal.add(e.getKey());
        }
        return legal.isEmpty() ? "NONE" : String.valueOf(legal);
    }

    /**
     * How many hours could be imported onto an EMPTY (group, date), greedily filling the day
     * left-anchored from the first calendar-open slot. Gate chain identical to SPIKE 1's
     * (conflict-free, {@link BoxRepair#passesPlacementGates}, {@link BoxRepair#isDepartureSafe})
     * with the VRP membership test replaced by {@link #vrpOkExceptAulaQuota} when
     * {@code relaxAulaQuota} is set - the counterfactual the spike exists to size. NOTHING is
     * committed: this is a read-only count on the pristine grid.
     */
    private static String donorReport(List<Lesson> lessons, StudentGroup group, LocalDate date,
                                      Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot,
                                      Map<Timeslot, List<Lesson>> byTs,
                                      Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                      Map<String, Integer> teacherDaily,
                                      boolean relaxAulaQuota) {
        int[] window = SchoolSchedule.getGroupCalendarWindow(group.id, date.getDayOfWeek(), date);
        if (window[1] < 0) return "calendar CLOSED";
        DailyActivityQuota row = SchoolSchedule.getExpectedHoursRow(group.id, date);
        int want = row.aulaHours + row.labHours;
        // Simulated day map, so each successive placement sees the growing day (H11/H17 arrival).
        TreeMap<Integer, Boolean> simDay = new TreeMap<>();
        Map<String, TreeMap<Integer, Boolean>> simGroupDaySlots = new HashMap<>(groupDaySlots);
        simGroupDaySlots.put(group.id + "@" + date, simDay);
        Map<String, Integer> simTeacherDaily = new HashMap<>(teacherDaily);
        Set<Lesson> used = new HashSet<>();
        List<String> taken = new ArrayList<>();
        for (int s = window[1]; s <= window[2] && taken.size() < want; s++) {
            Timeslot target = tsByDateSlot.getOrDefault(date, Map.of()).get(s);
            if (target == null) continue;
            Lesson pick = null;
            for (Lesson lesson : lessons) {
                if (used.contains(lesson) || lesson.pinned || lesson.timeslot == null) continue;
                StudentGroup g = lesson.getStudentGroup();
                if (g == null || !g.id.equals(group.id)) continue;
                if (date.equals(lesson.timeslot.getDate())) continue;
                if (!StudentDayCompaction.isSlotConflictFree(lesson, target, byTs)) continue;
                if (!BoxRepair.passesPlacementGates(lesson, target, simGroupDaySlots, simTeacherDaily)) continue;
                if (!BoxRepair.isDepartureSafe(lesson, groupDaySlots)) continue;
                boolean vrpOk = relaxAulaQuota
                        ? vrpOkExceptAulaQuota(lesson, target)
                        : lesson.getAvailableTimeslots().contains(target);
                if (!vrpOk) continue;
                pick = lesson;
                break;
            }
            if (pick == null) break;
            used.add(pick);
            simDay.put(s, false);
            Teacher t = pick.getTeacher();
            if (t != null && !t.pseudo) simTeacherDaily.merge(t.id + "@" + date, 1, Integer::sum);
            taken.add("s" + s + ":" + abbrev(pick.getSubject()) + "/" + teacherOf(pick)
                    + "@" + pick.timeslot.getDate());
        }
        return taken.size() + "/" + want + " hours placeable " + taken;
    }

    /**
     * The VRP filter chain of {@link Lesson#getAvailableTimeslots()} re-evaluated filter by
     * filter against the SAME published statics, MINUS the aula half of the L1-bis quota filter
     * (H24) - the one relaxation this counterfactual is about. Every other filter is delegated
     * to the production decision method it belongs to ({@link Lesson#isTeacherUnavailable},
     * {@link GroupCalendarService#isOpen}, {@link DailyActivityQuota#permits} for the LAB half,
     * {@link DailyActivityQuota#isStageDate}, {@link SchoolSchedule#isNoSessionDate}), exactly
     * as {@code Task419H17GapAnatomyTest}'s own {@code whyNot} does. A counterfactual cannot ask
     * the cached value range this question, so the chain is unavoidable here.
     */
    private static boolean vrpOkExceptAulaQuota(Lesson lesson, Timeslot ts) {
        if (lesson.pinned) return false;
        Set<LocalDate> holidays = SchoolSchedule.getSchoolHolidaysStatic();
        if (holidays != null && holidays.contains(ts.getDate())) return false;                    // H6.1
        Long teacherId = lesson.getTeacher() != null ? lesson.getTeacher().id : null;
        if (Lesson.isTeacherUnavailable(ts, teacherId, SchoolSchedule.getTeacherAvailabilitiesStatic())) {
            return false;                                                                          // H5.1
        }
        StudentGroup group = lesson.getStudentGroup();
        if (group == null) return true;
        boolean pseudo = lesson.getTeacher() != null && lesson.getTeacher().pseudo;
        Map<Long, Set<LocalDate>> stageBlackout = SchoolSchedule.getStageBlackoutDatesStatic();    // H4.1
        if (lesson.course != null && !lesson.course.professionalizzanti && !pseudo
                && stageBlackout != null && stageBlackout.containsKey(group.id)
                && stageBlackout.get(group.id).contains(ts.getDate())) {
            return false;
        }
        Map<Long, Set<Timeslot>> pinnedMap = SchoolSchedule.getPinnedLessonTimeslotsByGroupStatic();
        if (pinnedMap != null && pinnedMap.get(group.id) != null
                && pinnedMap.get(group.id).contains(ts)) return false;                             // H3 VRP
        if (!GroupCalendarService.isOpen(SchoolSchedule.getGroupCalendarRulesForGroup(group.id), ts)) {
            return false;                                                                          // H20
        }
        Map<LocalDate, DailyActivityQuota> quotas = SchoolSchedule.getDailyActivityQuotasForGroup(group.id);
        if (quotas != null && !quotas.isEmpty() && !pseudo) {
            boolean bound = DailyActivityQuota.isQuotaBoundLabSubject(group.name, lesson.getSubject());
            // THE relaxation: the lab half (H23) still binds, the aula half (H24) is bypassed.
            if (bound && !DailyActivityQuota.permits(quotas.get(ts.getDate()), true)) return false;
        }
        if (lesson.isStageConfined()
                && !DailyActivityQuota.isStageDate(SchoolSchedule.getExpectedHoursRow(group.id, ts.getDate()))) {
            return false;                                                                          // TASK-384
        }
        return pseudo || !SchoolSchedule.isNoSessionDate(group.id, ts.getDate());                  // H26
    }

    // ───────────────────────── helpers ─────────────────────────

    private static String teacherOf(Lesson l) {
        return l.getTeacher() != null ? l.getTeacher().name : "?";
    }

    private static String abbrev(String s) {
        if (s == null) return "?";
        return s.length() <= 14 ? s : s.substring(0, 14);
    }
}
