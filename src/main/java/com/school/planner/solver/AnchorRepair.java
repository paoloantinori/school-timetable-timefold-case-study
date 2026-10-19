package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jboss.logging.Logger;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;

/**
 * TASK-433 (ADOPTED, ratified maintainer 2026-07-28): deterministic late-start (M18 day-start)
 * repair - closes a day's LEADING hole(s) by IMPORTING an hour from another day of the same
 * group into the leading slot, so the day starts at its first calendar-open slot instead of
 * offset slots later. The repair counterpart to TASK-430's slot-0 reservation pre-pass: 430
 * PREVENTS the late-start at construction, this REPAIRS it post-smarcatura; both target the
 * same H1 cross-group slot-0 saturation residual the TASK-426 day-start anatomy isolated (the
 * spike measured 89 -> 16 late days on train2, 82 -> 20 on m12, with the M18-guard).
 *
 * <p><b>Sibling of {@link HoleRepair}, not a near-copy of it.</b> HoleRepair closes MID-day
 * H17 breaks; AnchorRepair closes LEADING M18 breaks. The mechanism is the same a cross-date
 * whole-hour import through the identical gate chain (conflict-free, {@link
 * BoxRepair#passesPlacementGates}, {@link BoxRepair#isDepartureSafe}, VRP membership last),
 * so it reuses {@link HoleRepair#commitCrossDayMove} as its commit and {@link
 * BoxRepair}/{@link StudentDayCompaction}'s live indexes - it adds only the leading-slot
 * targeting and the M18 donor guard. The donor picker mirrors {@link HoleRepair}'s two-level
 * A4-priced ranking (off-template free import first, then cheapest on-template), so the M32
 * damage is bounded by MAX and the donor is taken from the deepest-surplus day first.
 *
 * <p><b>M18-guard ALWAYS ON (the lesson of the spike).</b> Importing a donor that is its day's
 * own first lesson would fix the target day's start by DE-ANCHORING the donor's - a pure shell
 * game the OFF run of the spike measured directly (53 donors de-anchored). So every donor must
 * pass {@link SurplusRebalance#keepsDayAnchored} (the same rule SurplusRebalance applies to its
 * own donors): taking it off its day must not raise that day's M18 offset. The {@code
 * donorDaysNewlyLate} field of {@link Result} is therefore always 0 and is asserted so at the
 * checkpoint.
 *
 * <p><b>Logical correctness over medium cost (the standing directive).</b> Closing a leading
 * hole costs medium: the import's M20 donor-day removal outweighs the M18 credit and the M20
 * target fill gain (the spike measured a net +40-45k medium for the full closure set). That is
 * accepted knowingly: late-start is a human-pattern goal (the golden has <=3 non-legitimate
 * late days/year + the first-day-at-9 rule), M18 is medium, and the pass runs LATE in the
 * staged cycle (post-HoleRepair, pre-SurplusRebalance; only stage B-ROOM follows, which moves
 * rooms only) so the trade is essentially final, not re-optimized away.
 *
 * <p><b>Scope.</b> A late day is one whose first occupied slot is past the group's first
 * calendar-open slot ({@link SchoolScheduleConstraintProvider#m18DayStartOffset} > 0). The
 * ceremonial first school day ({@link TimeslotSequence#SCHOOL_YEAR_START}, when the whole
 * school starts one slot late by design) and closed-day calendar sentinels are exempt. Other
 * golden-legitimate late days (a handful the golden itself starts late) are NOT exempt here:
 * the production pass has no access to the golden's per-(group,date) late set, so it is
 * fact-faithful (calendar-open), and the few golden-legitimate cases it would over-fix are a
 * known gate watch-item, not a correctness bug.
 *
 * <p><b>ADOPTED (ratified maintainer 2026-07-28).</b> Runs unconditionally in the staged cycle
 * post-HoleRepair / pre-SurplusRebalance (ScheduleService.runAnchorRepair); the 3-run gate
 * evidence showed day-start 82 -> 0/1/13 late days with 0 conflicts and 0 newly-late donors.
 * Like {@link HoleRepair#repair}, it MUST run post-smarcatura (no promoted pins held) and
 * throws if not.
 */
public final class AnchorRepair {

    private static final Logger LOG = Logger.getLogger(AnchorRepair.class);

    /** Safety belt on committed moves per pass (a sane post-smarcatura grid has well under 100 late days). */
    static final int MAX_MOVES_PER_PASS = 200;

    private AnchorRepair() {
    }

    /**
     * {@code lateDaysBefore/After} = M18-late (group, date) days (offset > 0 vs the first
     * calendar-open slot, ceremonial first day and closed sentinels exempt) - the metric this
     * pass exists to reduce; {@code slotsFilled} = leading slots filled by import; the three
     * {@code closed*} counters are the per-day closability tally (fully / partial / none);
     * {@code donorDaysEmptied} / {@code donorDaysNewlyLate} track the donor-side collateral
     * (the latter MUST be 0 - the M18-guard forbids de-anchoring); {@code conflictsAdded}
     * must be 0 (the commit is conflict-free by construction).
     */
    public record Result(int lateDaysBefore, int lateDaysAfter, int slotsFilled,
                         int fullyClosed, int partiallyClosed, int notClosed,
                         int donorDaysEmptied, int donorDaysNewlyLate, long conflictsAdded) {
    }

    public static Result repair(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot,
                                Consumer<Lesson> nullRoom) {
        if (schedule.getTimeslots() == null) {
            return new Result(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        // Post-smarcatura only, exactly like HoleRepair: the donor pool reads the raw pinned
        // flag, which equals authoritative-pin only once every promoted pin has been released.
        if (!SchoolSchedule.getPromotedLessonIdsStatic().isEmpty()) {
            throw new IllegalStateException("AnchorRepair.repair called while " +
                    SchoolSchedule.getPromotedLessonIdsStatic().size()
                    + " promoted pin(s) are held - this pass is designed for the post-release grid only");
        }

        // Live indexes, built once and kept honest through every commit (commitCrossDayMove
        // updates byTs/groupDaySlots/teacherDaily/dayLessons in place).
        Map<Timeslot, List<Lesson>> byTs = StudentDayCompaction.buildOccupancyIndex(schedule.getLessons());
        Map<String, TreeMap<Integer, Boolean>> groupDaySlots = BoxRepair.buildGroupDaySlots(schedule.getLessons());
        Map<String, Integer> teacherDaily = BoxRepair.buildTeacherDaily(schedule.getLessons());
        Map<String, TreeMap<Integer, Lesson>> dayLessons = HoleRepair.buildDayLessons(schedule.getLessons());
        Map<LocalDate, Map<Integer, Timeslot>> tsByDateSlot =
                StudentDayCompaction.buildTimeslotsByDateAndSlot(schedule.getTimeslots());
        // Per-group donor pools (shared builder with HoleRepair): the import's gate chain and
        // A4 cost live in HoleRepair.findImport, reused below with the M18-guard ON.
        Map<Long, List<Lesson>> candidatesByGroup = HoleRepair.buildCandidatesByGroup(schedule.getLessons());

        List<LateDay> lateDays = collectLateDays(dayLessons);
        int lateBefore = lateDays.size();
        if (lateBefore == 0) {
            return new Result(0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        // Deterministic order (group then date) so the pass is reproducible across re-seeded profiles.
        lateDays.sort(Comparator
                .comparing((LateDay d) -> d.group.name)
                .thenComparing(d -> d.date));

        long conflictsBefore = StudentDayCompaction.countConflicts(schedule);
        int fullyClosed = 0;
        int partiallyClosed = 0;
        int notClosed = 0;
        int slotsFilled = 0;
        int donorDaysEmptied = 0;
        int donorDaysNewlyLate = 0;

        for (LateDay day : lateDays) {
            if (slotsFilled >= MAX_MOVES_PER_PASS) {
                LOG.warnf("AnchorRepair hit MAX_MOVES_PER_PASS (%d); remaining late days left untouched", MAX_MOVES_PER_PASS);
                break;
            }
            int filled = 0;
            // Adjacent-slot-first, distinct movers: fill slot minSlot-1, then minSlot-2, ... down
            // to minOpen. Filling the far slot first would open an H17 break the arrival gate
            // refuses, which is the correct modelling of the joint requirement.
            for (int s = day.minSlot - 1; s >= day.minOpen; s--) {
                Timeslot target = tsByDateSlot.getOrDefault(day.date, Map.of()).get(s);
                if (target == null) {
                    break; // off-grid slot: nothing to fill here
                }
                Lesson pick = findAnchorImportDonor(candidatesByGroup.getOrDefault(day.group.id, List.of()),
                        day.date, target, byTs, groupDaySlots, teacherDaily);
                if (pick == null) {
                    break; // no legal donor reaches this leading slot: leave the rest of the day's lead
                }
                LocalDate originDate = pick.timeslot.getDate();
                String originKey = day.group.id + "@" + originDate;
                TreeMap<Integer, Lesson> originDay = dayLessons.get(originKey);
                int originMinBefore = originDay.firstKey();
                int originSizeBefore = originDay.size();

                HoleRepair.commitCrossDayMove(pick, target, byTs, groupDaySlots, teacherDaily, dayLessons,
                        moveTimeslot, nullRoom);
                filled++;
                slotsFilled++;
                if (originSizeBefore == 1) {
                    donorDaysEmptied++;
                } else if (originDay.firstKey() > originMinBefore
                        && SchoolScheduleConstraintProvider.m18DayStartOffset(day.group, originDate, originDay.firstKey()) > 0
                        && SchoolScheduleConstraintProvider.m18DayStartOffset(day.group, originDate, originMinBefore) == 0) {
                    donorDaysNewlyLate++;
                }
            }
            if (filled == day.offset) {
                fullyClosed++;
            } else if (filled > 0) {
                partiallyClosed++;
            } else {
                notClosed++;
            }
        }

        int lateAfter = countLateDays(dayLessons);
        long conflictsAdded = StudentDayCompaction.countConflicts(schedule) - conflictsBefore;
        LOG.infof("AnchorRepair (TASK-433 candidate): late days %d->%d (filled %d leading slots; fully %d, partial %d, none %d); "
                        + "donor days emptied %d, newly-late %d, conflicts added %d",
                lateBefore, lateAfter, slotsFilled, fullyClosed, partiallyClosed, notClosed,
                donorDaysEmptied, donorDaysNewlyLate, conflictsAdded);
        return new Result(lateBefore, lateAfter, slotsFilled, fullyClosed, partiallyClosed, notClosed,
                donorDaysEmptied, donorDaysNewlyLate, conflictsAdded);
    }

    /** Every (group, date) whose first occupied slot is past its first calendar-open slot (M18 offset > 0), ceremonial first day and closed sentinels exempt. */
    private static List<LateDay> collectLateDays(Map<String, TreeMap<Integer, Lesson>> dayLessons) {
        List<LateDay> lateDays = new ArrayList<>();
        for (Map.Entry<String, TreeMap<Integer, Lesson>> entry : dayLessons.entrySet()) {
            TreeMap<Integer, Lesson> bySlot = entry.getValue();
            if (bySlot.isEmpty()) continue;
            Lesson any = bySlot.firstEntry().getValue();
            StudentGroup group = any.getStudentGroup();
            if (group == null) continue;
            LocalDate date = any.timeslot.getDate();
            int minOpen = SchoolSchedule.getGroupCalendarWindow(group.id, date.getDayOfWeek(), date)[1];
            if (minOpen < 0) continue; // closed-day sentinel: M18 does not penalize it (H20's business)
            int minSlot = bySlot.firstKey();
            int offset = SchoolScheduleConstraintProvider.m18DayStartOffset(group, date, minSlot);
            if (offset <= 0) continue;
            if (TimeslotSequence.SCHOOL_YEAR_START.equals(date)) continue; // ceremonial first school day
            lateDays.add(new LateDay(entry.getKey(), group, date, minOpen, minSlot, offset));
        }
        return lateDays;
    }

    /** Re-measure of M18-late days on the post-pass grid (the honest "after" number). */
    private static int countLateDays(Map<String, TreeMap<Integer, Lesson>> dayLessons) {
        int late = 0;
        for (Map.Entry<String, TreeMap<Integer, Lesson>> entry : dayLessons.entrySet()) {
            TreeMap<Integer, Lesson> bySlot = entry.getValue();
            if (bySlot.isEmpty()) continue;
            Lesson any = bySlot.firstEntry().getValue();
            StudentGroup group = any.getStudentGroup();
            if (group == null) continue;
            LocalDate date = any.timeslot.getDate();
            if (SchoolSchedule.getGroupCalendarWindow(group.id, date.getDayOfWeek(), date)[1] < 0) continue;
            if (TimeslotSequence.SCHOOL_YEAR_START.equals(date)) continue;
            if (SchoolScheduleConstraintProvider.m18DayStartOffset(group, date, bySlot.firstKey()) > 0) late++;
        }
        return late;
    }

    /**
     * The anchor-import donor: a thin two-level wrapper over {@link HoleRepair#findImport}
     * (off-template free import before cheapest on-template), passing the M18 donor-anchor
     * guard ON ({@code true}). The whole gate chain (conflict-free, {@link
     * BoxRepair#passesPlacementGates}, {@link BoxRepair#isDepartureSafe}, the M18 guard, VRP
     * membership last) and the A4 cost key (M32 weight + M20 source-day removal) live in
     * {@link HoleRepair#findImport} and are shared verbatim with the mid-day H17 repair, so a
     * change to the donor gate chain is one edit, not two. {@code pool} is the group's
     * pre-filtered donor pool ({@link HoleRepair#buildCandidatesByGroup}).
     */
    private static Lesson findAnchorImportDonor(List<Lesson> pool, LocalDate holeDate, Timeslot target,
                                                Map<Timeslot, List<Lesson>> byTs,
                                                Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                                Map<String, Integer> teacherDaily) {
        for (boolean onTemplate : new boolean[] { false, true }) {
            Lesson pick = HoleRepair.findImport(pool, target, holeDate, onTemplate, byTs, groupDaySlots,
                    teacherDaily, true);
            if (pick != null) return pick;
        }
        return null;
    }

    private record LateDay(String key, StudentGroup group, LocalDate date, int minOpen, int minSlot, int offset) {
    }
}
