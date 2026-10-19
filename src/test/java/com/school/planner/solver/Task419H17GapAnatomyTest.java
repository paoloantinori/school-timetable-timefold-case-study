package com.school.planner.solver;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.AvailabilityExpansionService;

/**
 * TASK-419 ANALYSIS-ONLY harness (no production code touched): anatomy of the H17
 * residual of a staged snapshot. For every non-exempt H17 break of the snapshot it
 * prints the bounding lessons (with their on-template / ex-promoted proxy status and
 * M32 weight), every legal filler candidate for the hole (VRP + conflict + caps),
 * and a compaction simulation that assumes the promoted pins are RELEASED - which
 * sizes "how many template cells would have to move to close this gap".
 *
 * <p>Also re-runs the box discriminator (A = a legal conflict-free cell exists, so
 * a repair/LS coverage miss; B = deadlocked) on the FINAL state.
 *
 * <p><b>W2 code review (2026-07-26): moved from {@code bootstrap} to this package.</b> The
 * original version (in {@code com.school.planner.bootstrap}) could not reach the real
 * package-private gate primitives ({@link StudentDayCompaction}, {@link BoxRepair}) and
 * re-implemented six of them as local twins - two of which (the H17-arrival/departure
 * checks) used RAW slot contiguity instead of the real break-level pinned-exemption
 * ({@link BoxRepair#h17DayPenalty}), which is STRICTER than the real gate: a day with a
 * pre-existing authoritative-pin-exempt gap elsewhere would read every cell as unsafe
 * through the twin, even though {@link BoxRepair#isH17Safe}/{@link
 * BoxRepair#isH17SafeDeparture} (the gates {@link HoleRepair} and {@link BoxRepair}
 * themselves use) would accept it. Moving the harness into this package lets it call the
 * real primitives directly, so its printed diagnostics can never disagree with what the
 * production repair passes actually do. This is a print-only harness (a single
 * {@code assertTrue(true)}), so no assertion values needed re-deriving; the funnel in the
 * box-discriminator section was reshaped (see below) because the real gates expose a
 * combined caps+H17 check, not the separate caps-only step the old twin invented.
 *
 * <p>Invoke: {@code PLANNER_TEST_H17_ANATOMY=/tmp/val419-staged-smoke2 ./scripts/core/test.sh -c Task419H17GapAnatomyTest}
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_H17_ANATOMY", matches = ".+")
class Task419H17GapAnatomyTest {

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

    private record Gap(String group, Long groupId, LocalDate date, int prevSlot, int nextSlot,
                       Lesson prev, Lesson next) {
        int width() {
            return nextSlot - prevSlot - 1;
        }
    }

    @Test
    @Transactional
    void printH17GapAnatomy() throws Exception {
        Path dir = SnapshotFixture.resolveSnapshotDir(System.getenv("PLANNER_TEST_H17_ANATOMY"));
        SnapshotFixture.Snapshot snap =
                SnapshotFixture.loadSnapshotNoClean(dir, availabilityExpansionService, true);
        SnapshotFixture.seedMediumTierFacts(dataGenerator, snap);

        List<Lesson> lessons = snap.lessons;
        // The real occupancy index (TASK-419: shared by StudentDayCompaction/BoxRepair/HoleRepair).
        Map<Timeslot, List<Lesson>> byTs = StudentDayCompaction.buildOccupancyIndex(lessons);
        // The real per-(group,date) slotIndex -> AUTHORITATIVELY-pinned map: the shape every
        // H17 gate here (isH17Safe/isH17SafeDeparture/passesPlacementGates) reads.
        Map<String, TreeMap<Integer, Boolean>> groupDaySlots = BoxRepair.buildGroupDaySlots(lessons);
        Map<String, Integer> teacherDaily = BoxRepair.buildTeacherDaily(lessons);
        Map<String, Timeslot> tsByDateSlot = new HashMap<>();
        for (Timeslot ts : snap.timeslots) {
            tsByDateSlot.put(ts.getDate() + "@" + TimeslotSequence.slotIndexOf(ts), ts);
        }
        // (groupId, date) -> slot -> lessons - a plain DATA view for display/simulation, not a
        // gate: dayShape/simulateSlide read it to print a human-readable day layout.
        Map<String, TreeMap<Integer, List<Lesson>>> byGroupDate = new TreeMap<>();
        for (Lesson l : lessons) {
            if (l.timeslot == null || l.getStudentGroup() == null) continue;
            byGroupDate.computeIfAbsent(l.getStudentGroup().name + "@" + l.timeslot.getDate(), k -> new TreeMap<>())
                    .computeIfAbsent(TimeslotSequence.slotIndexOf(l.timeslot), k -> new ArrayList<>()).add(l);
        }

        System.out.println("=== TASK-419 H17 gap anatomy: " + dir + " ===");
        long placed = lessons.stream().filter(l -> l.timeslot != null).count();
        System.out.printf("  population: %d lessons (%d placed, %d boxed), skippedClosedDateRows=%d%n",
                lessons.size(), placed, lessons.size() - placed, snap.skippedClosedDateRows);
        long onTemplate = lessons.stream().filter(l -> l.timeslot != null && !l.pinned && isOnTemplate(l)).count();
        long offTemplate = lessons.stream().filter(l -> l.timeslot != null && !l.pinned && !isOnTemplate(l)).count();
        System.out.printf("  unpinned placed: on-template (ex-promoted proxy)=%d, off-template (free tail)=%d%n",
                onTemplate, offTemplate);

        // ── 1. enumerate the non-exempt H17 breaks (same semantics as verticallyCompact) ──
        List<Gap> gaps = new ArrayList<>();
        int gapSlots = 0;
        for (Map.Entry<String, TreeMap<Integer, List<Lesson>>> e : byGroupDate.entrySet()) {
            TreeMap<Integer, List<Lesson>> bySlot = e.getValue();
            List<Integer> slots = new ArrayList<>(bySlot.keySet());
            for (int i = 0; i + 1 < slots.size(); i++) {
                int a = slots.get(i), b = slots.get(i + 1);
                if (b - a <= 1) continue;
                Lesson prev = bySlot.get(a).get(0);
                Lesson next = bySlot.get(b).get(0);
                boolean exempt = bySlot.get(a).stream().allMatch(l -> l.pinned)
                        && bySlot.get(b).stream().allMatch(l -> l.pinned);
                if (exempt) continue;
                gaps.add(new Gap(prev.getStudentGroup().name, prev.getStudentGroup().id,
                        prev.timeslot.getDate(), a, b, prev, next));
                gapSlots += b - a - 1;
            }
        }
        gaps.sort(Comparator.comparing(Gap::group).thenComparing(Gap::date));
        System.out.printf("%n  H17 non-exempt breaks: %d (gap slots = %d, i.e. hard -%d)%n%n",
                gaps.size(), gapSlots, gapSlots);

        // ── 2. per-gap anatomy ──
        int idx = 0;
        for (Gap gap : gaps) {
            idx++;
            System.out.printf("── gap #%d  %s  %s (%s)  slots %d..%d empty (width %d)%n",
                    idx, gap.group(), gap.date(), gap.date().getDayOfWeek(),
                    gap.prevSlot() + 1, gap.nextSlot() - 1, gap.width());
            System.out.printf("     day shape: %s%n", dayShape(byGroupDate, gap));
            System.out.printf("     prev  slot %d: %-38s %-12s %s (M32 w=%d)%n", gap.prevSlot(),
                    gap.prev().getSubject(), teacherOf(gap.prev()),
                    tag(gap.prev()), weightOf(gap.prev()));
            System.out.printf("     next  slot %d: %-38s %-12s %s (M32 w=%d)%n", gap.nextSlot(),
                    gap.next().getSubject(), teacherOf(gap.next()),
                    tag(gap.next()), weightOf(gap.next()));

            // fillers: only same-group unpinned lessons can fill a group's hole. "Legal" here
            // mirrors HoleRepair's own findImport: VRP + conflict-free + the real placement
            // gate (H14/H11/H7/H17-arrival, BoxRepair.passesPlacementGates) + H17-safe departure
            // for a lesson coming off another day.
            for (int s = gap.prevSlot() + 1; s < gap.nextSlot(); s++) {
                Timeslot ts = tsByDateSlot.get(gap.date() + "@" + s);
                if (ts == null) {
                    System.out.printf("     slot %d: NO TIMESLOT on the grid (structurally unfillable)%n", s);
                    continue;
                }
                List<Lesson> boxedOk = new ArrayList<>();
                List<Lesson> freeOk = new ArrayList<>();
                List<Lesson> freeOkDepartureSafe = new ArrayList<>();
                List<Lesson> templOk = new ArrayList<>();
                for (Lesson l : lessons) {
                    if (l.pinned || l.getStudentGroup() == null) continue;
                    if (!l.getStudentGroup().id.equals(gap.groupId())) continue;
                    if (l.timeslot != null && l.timeslot.getDate().equals(gap.date())) continue; // same-day = the slide case
                    if (!l.getAvailableTimeslots().contains(ts)) continue;
                    if (!StudentDayCompaction.isSlotConflictFree(l, ts, byTs)) continue;
                    if (!BoxRepair.passesPlacementGates(l, ts, groupDaySlots, teacherDaily)) continue;
                    if (l.timeslot == null) boxedOk.add(l);
                    else if (isOnTemplate(l)) templOk.add(l);
                    else {
                        freeOk.add(l);
                        if (BoxRepair.isDepartureSafe(l, groupDaySlots)) freeOkDepartureSafe.add(l);
                    }
                }
                System.out.printf("     slot %d fillers: boxed=%d, FREE off-template=%d (departure-safe %d), on-template=%d%n",
                        s, boxedOk.size(), freeOk.size(), freeOkDepartureSafe.size(), templOk.size());
                printSome("        free ", freeOkDepartureSafe.isEmpty() ? freeOk : freeOkDepartureSafe);
                printSome("        templ", templOk);
                printSome("        boxed", boxedOk);
            }

            // why can't the day's OWN lessons slide into the hole?
            for (int s = gap.prevSlot() + 1; s < gap.nextSlot(); s++) {
                Timeslot ts = tsByDateSlot.get(gap.date() + "@" + s);
                if (ts == null) continue;
                StringBuilder sb = new StringBuilder("     why-not (day's own lessons -> slot " + s + "): ");
                for (Map.Entry<Integer, List<Lesson>> e :
                        byGroupDate.get(gap.group() + "@" + gap.date()).entrySet()) {
                    Lesson l = e.getValue().get(0);
                    sb.append('s').append(e.getKey()).append('=')
                            .append(whyNot(l, ts, byTs)).append("  ");
                }
                System.out.println(sb);
            }

            // slide simulation (compaction with the promoted pins RELEASED)
            SlideResult slide = simulateSlide(gap, byGroupDate, tsByDateSlot, byTs);
            System.out.printf("     slide sim (promoted released): moves=%d, gapsAfter=%d, movedOnTemplate=%d, M32 cost=%d%s%n",
                    slide.moves, slide.gapsAfter, slide.movedOnTemplate, slide.m32Cost,
                    slide.blockedReason == null ? "" : "  [blocked: " + slide.blockedReason + "]");
            System.out.println();
        }

        // ── 2b. GLOBAL sizing: how many of the gap slots can a free-tail import close,
        //        each free lesson used at most once, occupancy kept live? ──
        System.out.println("── global sizing: free-tail import (each lesson used once) ──");
        java.util.Set<Lesson> used = new java.util.HashSet<>();
        int closedByFree = 0;
        List<String> unclosed = new ArrayList<>();
        for (Gap gap : gaps) {
            for (int s = gap.prevSlot() + 1; s < gap.nextSlot(); s++) {
                Timeslot ts = tsByDateSlot.get(gap.date() + "@" + s);
                if (ts == null) continue;
                Lesson pick = null;
                for (Lesson l : lessons) {
                    if (used.contains(l) || l.pinned || l.getStudentGroup() == null) continue;
                    if (!l.getStudentGroup().id.equals(gap.groupId())) continue;
                    if (l.timeslot != null && l.timeslot.getDate().equals(gap.date())) continue;
                    if (l.timeslot != null && isOnTemplate(l)) continue;      // free tail only
                    if (!l.getAvailableTimeslots().contains(ts)) continue;
                    if (!StudentDayCompaction.isSlotConflictFree(l, ts, byTs)) continue;
                    if (!BoxRepair.passesPlacementGates(l, ts, groupDaySlots, teacherDaily)) continue;
                    if (!BoxRepair.isDepartureSafe(l, groupDaySlots)) continue;
                    pick = l;
                    break;
                }
                if (pick != null) {
                    used.add(pick);
                    byTs.computeIfAbsent(ts, k -> new ArrayList<>()).add(pick); // occupancy stays honest
                    closedByFree++;
                    System.out.printf("  %s %s slot %d  <-  %s/%s from %s%n", gap.group(), gap.date(), s,
                            pick.getSubject(), teacherOf(pick),
                            pick.timeslot == null ? "the box" : pick.timeslot.getDate()
                                    + "s" + TimeslotSequence.slotIndexOf(pick.timeslot));
                } else {
                    unclosed.add(gap.group() + " " + gap.date() + " slot " + s);
                }
            }
        }
        System.out.printf("  free-tail import closes %d of %d gap slots; NOT closable: %s%n%n",
                closedByFree, gapSlots, unclosed);

        // ── 2b-bis. same greedy, now ALLOWING on-template (ex-promoted) imports: each one is
        //           a demotion, priced at its M32 weight ──
        System.out.println("── global sizing: on-template import for the slots the free tail cannot close ──");
        int closedByTemplate = 0;
        int m32Bill = 0;
        List<String> stillOpen = new ArrayList<>();
        for (Gap gap : gaps) {
            for (int s = gap.prevSlot() + 1; s < gap.nextSlot(); s++) {
                String key = gap.group() + " " + gap.date() + " slot " + s;
                if (!unclosed.contains(key)) continue;
                Timeslot ts = tsByDateSlot.get(gap.date() + "@" + s);
                Lesson pick = null;
                for (Lesson l : lessons) {
                    if (used.contains(l) || l.pinned || l.timeslot == null || l.getStudentGroup() == null) continue;
                    if (!l.getStudentGroup().id.equals(gap.groupId())) continue;
                    if (l.timeslot.getDate().equals(gap.date())) continue;
                    if (!isOnTemplate(l)) continue;
                    if (!l.getAvailableTimeslots().contains(ts)) continue;
                    if (!StudentDayCompaction.isSlotConflictFree(l, ts, byTs)) continue;
                    if (!BoxRepair.passesPlacementGates(l, ts, groupDaySlots, teacherDaily)) continue;
                    if (!BoxRepair.isDepartureSafe(l, groupDaySlots)) continue;
                    pick = l;
                    break;
                }
                if (pick != null) {
                    used.add(pick);
                    byTs.computeIfAbsent(ts, k -> new ArrayList<>()).add(pick);
                    closedByTemplate++;
                    m32Bill += weightOf(pick);
                    System.out.printf("  %s slot %d  <-  DEMOTE %s/%s from %ss%d (M32 w=%d)%n",
                            key, s, pick.getSubject(), teacherOf(pick), pick.timeslot.getDate(),
                            TimeslotSequence.slotIndexOf(pick.timeslot), weightOf(pick));
                } else {
                    stillOpen.add(key);
                }
            }
        }
        System.out.printf("  on-template import closes %d more gap slot(s) (M32 bill %d medium); still open: %s%n%n",
                closedByTemplate, m32Bill, stillOpen);

        // ── 2c. min-slide: move the day's FIRST lesson UP into the hole (the branch
        //        StudentDayCompaction does NOT have - it only slides the max lesson down) ──
        System.out.println("── min-slide feasibility (move the day's first lesson up into the hole) ──");
        for (Gap gap : gaps) {
            TreeMap<Integer, List<Lesson>> day = byGroupDate.get(gap.group() + "@" + gap.date());
            int lo = day.firstKey();
            Lesson min = day.get(lo).get(0);
            Timeslot target = tsByDateSlot.get(gap.date() + "@" + (gap.prevSlot() + 1));
            boolean onlyLeading = lo == gap.prevSlot();   // the hole is right after the first run of 1
            String verdict;
            if (min.pinned) verdict = "min lesson is an AUTHORITATIVE pin";
            else if (target == null) verdict = "no timeslot";
            else if (!min.getAvailableTimeslots().contains(target)) verdict = "VRP forbids";
            else if (!StudentDayCompaction.isSlotConflictFree(min, target, byTs)) verdict = "conflict";
            else verdict = "LEGAL" + (onlyLeading ? " and CLOSES the gap" : " but only SHIFTS the hole")
                        + (isOnTemplate(min) ? " (min is ON-TEMPLATE, M32 cost " + weightOf(min) + ")" : " (min is free)");
            System.out.printf("  %s %s: min slot %d %s/%s -> slot %d : %s%n", gap.group(), gap.date(), lo,
                    min.getSubject(), teacherOf(min), gap.prevSlot() + 1, verdict);
        }
        System.out.println();

        // ── 3. box discriminator on the FINAL state ──
        // Funnel reshaped vs the old bootstrap-package twin (see class javadoc): the real
        // gates expose isH17Safe (H17-arrival ALONE) and passesPlacementGates (H14+caps+H17,
        // all four required gates combined) - there is no exposed "caps ok, ignoring H17" step,
        // so the funnel is range -> conflict-free -> H17-arrival-safe (ignoring caps) ->
        // fully-legal (BoxRepair's own real gate). h17SafeIgnoringCaps is diagnostic only: a
        // SUPERSET of fullyLegal, it isolates whether H17 or H14/caps is the actual blocker.
        System.out.println("── boxed-lesson discriminator (final state) ──");
        for (Lesson l : snap.boxedLessons) {
            List<Timeslot> range = l.getAvailableTimeslots();
            List<Timeslot> conflictFree = new ArrayList<>();
            List<Timeslot> h17SafeIgnoringCaps = new ArrayList<>();
            List<Timeslot> fullyLegal = new ArrayList<>();
            for (Timeslot ts : range) {
                if (!StudentDayCompaction.isSlotConflictFree(l, ts, byTs)) continue;
                conflictFree.add(ts);
                TreeMap<Integer, Boolean> daySlots = l.getStudentGroup() != null
                        ? groupDaySlots.get(l.getStudentGroup().id + "@" + ts.getDate()) : null;
                if (BoxRepair.isH17Safe(daySlots, TimeslotSequence.slotIndexOf(ts))) h17SafeIgnoringCaps.add(ts);
                if (BoxRepair.passesPlacementGates(l, ts, groupDaySlots, teacherDaily)) fullyLegal.add(ts);
            }
            System.out.printf("  %s / %s / %s%n", l.getStudentGroup().name, teacherOf(l), l.getSubject());
            System.out.printf("     VRP cells=%d, conflict-free=%d, H17-arrival-safe (ignoring caps)=%d, "
                            + "+fully legal (H14/caps/H17)=%d%n",
                    range.size(), conflictFree.size(), h17SafeIgnoringCaps.size(), fullyLegal.size());
            System.out.printf("     verdict: %s%n", conflictFree.isEmpty()
                    ? "B (deadlocked - no conflict-free cell at all)"
                    : (fullyLegal.isEmpty()
                    ? "A' (conflict-free cells exist but NONE passes BoxRepair's own placement gate)"
                    : "A (a legal, gate-passing cell exists - repair/LS coverage miss)"));
            printCells("     first conflict-free cells: ", conflictFree);
            printCells("     first fully-legal cells:      ", fullyLegal);
            // would a DEMOTION open a fully-legal cell? classify the blockers of every
            // H17-safe-arrival-but-occupied cell (the real per-cell H17 gate, ignoring caps -
            // a demotion only ever changes WHO occupies a cell, never H14/H7/H11).
            int blockedByAuthoritative = 0, blockedByTemplateOnly = 0, blockedByFreeOnly = 0, blockedMixed = 0;
            List<String> demotableCells = new ArrayList<>();
            for (Timeslot ts : range) {
                if (StudentDayCompaction.isSlotConflictFree(l, ts, byTs)) continue;
                TreeMap<Integer, Boolean> daySlots = l.getStudentGroup() != null
                        ? groupDaySlots.get(l.getStudentGroup().id + "@" + ts.getDate()) : null;
                if (!BoxRepair.isH17Safe(daySlots, TimeslotSequence.slotIndexOf(ts))) continue;
                boolean anyPinned = false, anyTemplate = false, anyFree = false;
                for (Lesson o : byTs.getOrDefault(ts, List.of())) {
                    if (o == l || !StudentDayCompaction.conflicts(o, l)) continue;
                    if (o.pinned) anyPinned = true;
                    else if (isOnTemplate(o)) anyTemplate = true;
                    else anyFree = true;
                }
                if (anyPinned) blockedByAuthoritative++;
                else if (anyTemplate && anyFree) blockedMixed++;
                else if (anyTemplate) {
                    blockedByTemplateOnly++;
                    if (demotableCells.size() < 6) {
                        demotableCells.add(ts.getDate() + "s" + TimeslotSequence.slotIndexOf(ts));
                    }
                } else if (anyFree) blockedByFreeOnly++;
            }
            System.out.printf("     H17-safe but OCCUPIED cells: authoritative-pin %d, ex-promoted only %d, free only %d, mixed %d%n",
                    blockedByAuthoritative, blockedByTemplateOnly, blockedByFreeOnly, blockedMixed);
            if (!demotableCells.isEmpty()) {
                System.out.println("     would open with ONE demotion: " + demotableCells);
            }
            // the day shape of every conflict-free (but not fully legal) cell, to price the tie
            for (Timeslot ts : conflictFree) {
                TreeMap<Integer, List<Lesson>> day =
                        byGroupDate.get(l.getStudentGroup().name + "@" + ts.getDate());
                TreeSet<Integer> slots = new TreeSet<>();
                if (day != null) slots.addAll(day.keySet());
                int before = slots.isEmpty() ? 0 : (slots.last() - slots.first() + 1) - slots.size();
                TreeSet<Integer> after = new TreeSet<>(slots);
                after.add(TimeslotSequence.slotIndexOf(ts));
                int afterGaps = (after.last() - after.first() + 1) - after.size();
                System.out.printf("     cell %s s%d: day slots %s -> H17 gap slots %d -> %d (H0 +1 traded for H17 -%d)%n",
                        ts.getDate(), TimeslotSequence.slotIndexOf(ts), slots, before, afterGaps,
                        afterGaps - before);
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(true);
    }

    // ───────────────────────── helpers ─────────────────────────

    private static String dayShape(Map<String, TreeMap<Integer, List<Lesson>>> byGroupDate, Gap gap) {
        TreeMap<Integer, List<Lesson>> bySlot = byGroupDate.get(gap.group() + "@" + gap.date());
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, List<Lesson>> e : bySlot.entrySet()) {
            Lesson l = e.getValue().get(0);
            sb.append(e.getKey()).append(':')
                    .append(abbrev(l.getSubject())).append('/').append(abbrev(teacherOf(l)))
                    .append(l.pinned ? "[PIN]" : (isOnTemplate(l) ? "[T]" : "[free]")).append(' ');
        }
        return sb.toString().trim();
    }

    private static String abbrev(String s) {
        if (s == null) return "?";
        return s.length() <= 12 ? s : s.substring(0, 12);
    }

    private static String teacherOf(Lesson l) {
        return l.getTeacher() != null ? l.getTeacher().name : "?";
    }

    /** Delegates to the real null-guard overload (code-review 2026-07-26): no local re-derivation. */
    private static boolean isOnTemplate(Lesson l) {
        return SchoolSchedule.isOnTemplateCell(l);
    }

    private static String tag(Lesson l) {
        return l.pinned ? "AUTHORITATIVE-PIN" : (isOnTemplate(l) ? "ON-TEMPLATE (ex-promoted)" : "FREE (off-template)");
    }

    private static int weightOf(Lesson l) {
        return StagedPinPolicy.deviationWeightOf(l);
    }

    private static void printSome(String prefix, List<Lesson> ls) {
        if (ls.isEmpty()) return;
        StringBuilder sb = new StringBuilder(prefix).append(": ");
        int n = 0;
        for (Lesson l : ls) {
            if (n++ >= 4) {
                sb.append("... (+").append(ls.size() - 4).append(')');
                break;
            }
            sb.append(l.getSubject()).append('/').append(teacherOf(l));
            sb.append(l.timeslot == null ? "[boxed]"
                    : "@" + l.timeslot.getDate() + "s" + TimeslotSequence.slotIndexOf(l.timeslot));
            sb.append("  ");
        }
        System.out.println(sb);
    }

    private static void printCells(String prefix, List<Timeslot> cells) {
        if (cells.isEmpty()) return;
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < Math.min(6, cells.size()); i++) {
            sb.append(cells.get(i).getDate()).append('s')
                    .append(TimeslotSequence.slotIndexOf(cells.get(i))).append(' ');
        }
        System.out.println(sb);
    }

    /** Which VRP filter (or which occupant) forbids {@code l} at {@code ts}: the same filter
     *  chain {@code Lesson.getAvailableTimeslots} runs, evaluated one by one for diagnosis. */
    private static String whyNot(Lesson l, Timeslot ts, Map<Timeslot, List<Lesson>> byTs) {
        if (l.pinned) return "PINNED";
        Long teacherId = l.getTeacher() != null ? l.getTeacher().id : null;
        if (Lesson.isTeacherUnavailable(ts, teacherId, SchoolSchedule.getTeacherAvailabilitiesStatic())) {
            return "H5-teacherUnavailable";
        }
        var group = l.getStudentGroup();
        if (group != null) {
            var stageBlackout = SchoolSchedule.getStageBlackoutDatesStatic();
            if (l.course != null && !l.course.professionalizzanti
                    && (l.getTeacher() == null || !l.getTeacher().pseudo)
                    && stageBlackout != null && stageBlackout.containsKey(group.id)
                    && stageBlackout.get(group.id).contains(ts.getDate())) {
                return "H4-stageBlackout";
            }
            var pinnedMap = SchoolSchedule.getPinnedLessonTimeslotsByGroupStatic();
            if (pinnedMap != null && pinnedMap.get(group.id) != null
                    && pinnedMap.get(group.id).contains(ts)) return "H3-pinnedCell";
            if (!com.school.planner.service.GroupCalendarService.isOpen(
                    SchoolSchedule.getGroupCalendarRulesForGroup(group.id), ts)) return "H20-calendarClosed";
            var quotas = SchoolSchedule.getDailyActivityQuotasForGroup(group.id);
            boolean quotaGroup = quotas != null && !quotas.isEmpty();
            boolean pseudo = l.getTeacher() != null && l.getTeacher().pseudo;
            if (quotaGroup && !pseudo) {
                boolean bound = com.school.planner.domain.DailyActivityQuota
                        .isQuotaBoundLabSubject(group.name, l.getSubject());
                if (!com.school.planner.domain.DailyActivityQuota.permits(quotas.get(ts.getDate()), bound)) {
                    return bound ? "H23-noLabQuota" : "H24-noAulaQuota";
                }
            }
            var expected = SchoolSchedule.getExpectedHoursForGroup(group.id);
            if (!pseudo && expected != null && !expected.isEmpty() && !expected.containsKey(ts.getDate())) {
                return "H26-noSessionDay";
            }
        }
        if (!l.getAvailableTimeslots().contains(ts)) return "VRP-other";
        List<Lesson> here = byTs.get(ts);
        if (here != null) {
            for (Lesson other : here) {
                if (other == l) continue;
                if (StudentDayCompaction.conflicts(other, l)) {
                    return "CONFLICT with " + other.getStudentGroup().name + "/" + teacherOf(other)
                            + "/" + abbrev(other.getSubject())
                            + (other.pinned ? "[PIN]" : (isOnTemplate(other)
                            ? "[T w=" + weightOf(other) + "]" : "[free]"));
                }
            }
        }
        return "OK";
    }

    private record SlideResult(int moves, int gapsAfter, int movedOnTemplate, int m32Cost, String blockedReason) {
    }

    /**
     * Simulate {@code StudentDayCompaction}'s move-the-max-lesson-into-the-lowest-gap loop on
     * this ONE (group, date), treating the promoted pins as RELEASED (only authoritative pins
     * immovable) - i.e. what a post-smarcatura compaction pass would achieve. This simulation
     * has no real counterpart in production (it deliberately mutates a scratch {@code bySlot}
     * map while ignoring already-relocated lessons in the LIVE {@code byTs} occupancy index,
     * see {@link #isSlotConflictFreeIgnoringRelocated}), so it stays local rather than calling
     * a shared primitive.
     */
    private SlideResult simulateSlide(Gap gap, Map<String, TreeMap<Integer, List<Lesson>>> byGroupDate,
                                      Map<String, Timeslot> tsByDateSlot,
                                      Map<Timeslot, List<Lesson>> byTs) {
        TreeMap<Integer, Lesson> bySlot = new TreeMap<>();
        for (Map.Entry<Integer, List<Lesson>> e : byGroupDate.get(gap.group() + "@" + gap.date()).entrySet()) {
            bySlot.put(e.getKey(), e.getValue().get(0));
        }
        int moves = 0, movedOnTemplate = 0, m32Cost = 0;
        String blocked = null;
        // lessons already relocated by this simulation: their entry in byTs is stale, so
        // occupancy checks must ignore them (their live position is bySlot's).
        java.util.Set<Lesson> relocated = new java.util.HashSet<>();
        for (int iter = 0; iter < TimeslotSequence.SLOTS_PER_DAY; iter++) {
            int lo = bySlot.firstKey(), hi = bySlot.lastKey();
            if (hi - lo + 1 == bySlot.size()) break;
            Lesson max = bySlot.get(hi);
            if (max.pinned) {
                blocked = "max-slot lesson is an AUTHORITATIVE pin";
                break;
            }
            boolean movedThis = false;
            for (int g = lo + 1; g < hi; g++) {
                if (bySlot.containsKey(g)) continue;
                Timeslot gapTs = tsByDateSlot.get(gap.date() + "@" + g);
                if (gapTs == null) continue;
                if (!max.getAvailableTimeslots().contains(gapTs)) continue;
                if (!isSlotConflictFreeIgnoringRelocated(max, gapTs, byTs, relocated)) continue;
                boolean wasOnTemplate = isOnTemplate(max);
                relocated.add(max);
                bySlot.remove(hi);
                bySlot.put(g, max);
                moves++;
                if (wasOnTemplate) {
                    movedOnTemplate++;
                    m32Cost += weightOf(max);
                }
                movedThis = true;
                break;
            }
            if (!movedThis) {
                blocked = "max-slot lesson (" + max.getSubject() + "/" + teacherOf(max)
                        + ") reaches no gap slot legally (VRP or conflict)";
                break;
            }
        }
        int gapsAfter = (bySlot.lastKey() - bySlot.firstKey() + 1) - bySlot.size();
        return new SlideResult(moves, gapsAfter, movedOnTemplate, m32Cost, blocked);
    }

    /** {@link StudentDayCompaction#isSlotConflictFree}, ignoring lessons this ONE-OFF simulation
     *  already relocated in its own scratch map (the live {@code byTs} still shows them at
     *  their pre-simulation cell) - not a gate reimplementation, since it delegates the actual
     *  conflict test to {@link StudentDayCompaction#conflicts}. */
    private static boolean isSlotConflictFreeIgnoringRelocated(Lesson mover, Timeslot ts,
                                                               Map<Timeslot, List<Lesson>> byTs,
                                                               java.util.Set<Lesson> relocated) {
        List<Lesson> here = byTs.get(ts);
        if (here == null) return true;
        for (Lesson other : here) {
            if (other == mover || relocated.contains(other)) continue;
            if (StudentDayCompaction.conflicts(mover, other)) return false;
        }
        return true;
    }
}
