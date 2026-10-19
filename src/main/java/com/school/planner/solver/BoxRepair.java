package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import com.school.planner.domain.Timeslot;

/**
 * TASK-401: deterministic box-clearing repair — places boxed lessons (unpinned,
 * {@code timeslot == null}, the TASK-398 H0 residual) into VRP-legal, conflict-free,
 * H17-safe cells of the current working solution. Run by {@link CompactionPhase}
 * (before and after its gap compaction), after the last timeslot local-search phase.
 *
 * <p><b>Why enumeration, not smarter moves</b> (TASK-399's discriminator measurement):
 * each lesson the LS leaves boxed has only ~3-7 feasible cells among ~1,000 VRP-legal
 * ones — a 1-in-~200 regime stochastic move selection reliably misses but a direct
 * scan trivially finds. This is the same loop {@code BoxVrpFeasibilityDiscriminatorTest}
 * uses to prove the cells exist, promoted to a repair that takes them.
 *
 * <p><b>Placement gates, all four required:</b>
 * <ul>
 * <li><b>VRP</b> — the cell is in {@link Lesson#getAvailableTimeslots()} (H4/H5/H6/H20/quota).</li>
 * <li><b>Conflict-free</b> — no placed lesson shares the cell's teacher (H1, non-pseudo)
 *     or group (H3), via the same authoritative timeslot→lessons index
 *     {@link StudentDayCompaction} uses.</li>
 * <li><b>Daily caps + start date</b> — unlike compaction (which moves within a day,
 *     so neither can change), the repair ADDS an hour to a possibly different day, so
 *     H11 ({@code group.maxHoursPerDay}), H7 ({@link Teacher#getEffectiveMaxDailyHours()},
 *     pseudo exempt) and H14 ({@code group.startDate}, which the VRP does NOT filter)
 *     are re-checked.</li>
 * <li><b>H17-safe</b> — the cell must not increase the group's day H17 penalty,
 *     computed with the REAL break-level pinned exemption (task-350: a break whose
 *     bounding lessons are BOTH AUTHORITATIVE pins scores 0, see {@link
 *     #buildGroupDaySlots}): {@link #h17DayPenalty} is evaluated
 *     before and after the simulated placement and the cell is taken only if the
 *     penalty does not grow. A naive adjacency/hole test is NOT enough — an unpinned
 *     lesson dropped inside a both-pinned-exempt gap flips that break from exempt to
 *     penalized (e.g. width-3 gap: H0 +1 but H17 -2, net -1 hard — the refutation the
 *     TASK-401 review's skeptic constructed). A cell that would grow the penalty is
 *     never taken; such a lesson stays boxed and is reported.</li>
 * </ul>
 *
 * <p><b>TASK-414: one-level eviction, the human planner's own move.</b> When the
 * direct pass cannot place L because EVERY VRP cell that passes L's non-conflict
 * gates (H14/cap/H17-arrival) is occupied, the grid has saturated L's compatible
 * cells (proven for the val410/G06 case: 216 Thursday VRP cells, 0 free). The
 * golden still solves it, so this is a search/repair limit, not a model limit
 * (primary directive, never water down Q22/M20.2/410). Eviction borrows a slot
 * the way a planner does: find a cell {@code ts} of L blocked by a SINGLE unpinned
 * occupant O, relocate O to a cell {@code ts2} that is fully legal FOR O (O's VRP,
 * conflict-free, O's caps, O's H17-safe ARRIVAL) AND whose DEPARTURE is H17-safe
 * (removing O from its own day does not open a penalized gap, the new gate), then
 * place L at {@code ts}. Net hard: −1 (H0), no new hard by construction (the exact
 * same gates as every placement, plus the departure gate).
 *
 * <p><b>Atomic two-move chain.</b> The full {@code (ts, O, ts2)} tuple is validated
 * against the live maps BEFORE either move is committed: O's relocation is proven
 * legal (all of O's gates, including departure) and L's placement is proven legal
 * (L's non-conflict gates + exactly-one-unpinned-blocker) in the same search. Only
 * then are both moves applied, in order (O→ts2, then L→ts). L's gates were validated
 * against the pre-commit maps; the swap is provably gate-preserving at commit time:
 * at {@code ts} L takes O's exact slot, so for a same-group blocker the group-day map
 * is unchanged by the swap, and for any blocker the H14/cap/H17-arrival invariants
 * validated with O present still hold once O is removed and L added (net-neutral or
 * improving, see {@link #passesNonConflictPlacementGates}). A relocation whose
 * departure would open a gap is never searched further, so O never moves to make
 * room for a placement that then fails.
 *
 * <p><b>Map maintenance (the new case: maps now SHRINK as well as grow).</b> Until
 * TASK-414 the byTs / groupDaySlots / teacherDaily maps only ever gained entries.
 * Evicting O removes its origin-slot from all three (byTs[ts] loses O;
 * groupDaySlots[O.group@originDate] loses O's slot; teacherDaily[O.teacher@originDate]
 * decrements) before O's destination entries and L's placement are added. Same-day
 * relocations (ts2 on O's origin date) apply remove-then-add to the SAME day map and
 * the SAME teacher count (net count unchanged), so the cap reads stay correct. The
 * canonical nested search order (L in (group,teacher,subject,id); ts and ts2 each in
 * {@code getAvailableTimeslots()}' chronological order; O is the single blocker at ts)
 * makes the repair deterministic and bounded (≤ L's VRP cells × 1 blocker × O's free
 * cells).
 *
 * <p><b>Latent, not gated (inert today):</b> H4.3 (professionalizzanti constant
 * single-day during stage) has neither a VRP filter nor a repair gate — it is
 * structurally inert while {@code ProfessionalizzantiDay} is seeded with zero rows
 * (its 2026/27 state, superseded by the golden import). If rows are ever seeded,
 * this repair needs a gate for it.
 *
 * <p><b>Improve-only:</b> every committed placement removes one H0 hard and can add no
 * hard violation by the gates above ({@code Result.conflictsAdded} must always be 0).
 * The room is nulled on placement (a boxed lesson normally has none, but a local
 * search pass may leave a stale one) so the room construction phase (ph4 since
 * TASK-420) assigns it conflict-free.
 */
public final class BoxRepair {

    private BoxRepair() {
    }

    /**
     * {@code boxedBefore/After} = unpinned null-timeslot lessons; {@code placed} = direct
     * placements; {@code evicted} = placements that required relocating one occupant
     * (TASK-414); {@code conflictsAdded} must be 0.
     */
    public record Result(int boxedBefore, int placed, int evicted, int boxedAfter, long conflictsAdded) {
    }

    public static Result repair(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot,
                                Consumer<Lesson> nullRoom) {
        if (schedule.getTimeslots() == null) {
            return new Result(0, 0, 0, 0, 0);
        }
        List<Lesson> boxed = new ArrayList<>();
        for (Lesson l : schedule.getLessons()) {
            if (!l.pinned && l.timeslot == null) boxed.add(l);
        }
        if (boxed.isEmpty()) {
            return new Result(0, 0, 0, 0, 0);
        }
        long conflictsBefore = StudentDayCompaction.countConflicts(schedule);

        // Occupancy + daily counts + per-(group, date) slot->authoritatively-pinned maps from
        // the placed population. The group's daily hour count IS its day map's size (the
        // population is conflict-free here, so no two same-group lessons share a slot index).
        // The per-slot flag is what h17DayPenalty's break-level exemption needs, and it is
        // AUTHORITATIVE pin, not raw pinned - see buildGroupDaySlots.
        Map<Timeslot, List<Lesson>> byTs = StudentDayCompaction.buildOccupancyIndex(schedule.getLessons());
        Map<String, Integer> teacherDaily = buildTeacherDaily(schedule.getLessons());
        Map<String, TreeMap<Integer, Boolean>> groupDaySlots = buildGroupDaySlots(schedule.getLessons());

        boxed.sort(Comparator
                .comparing((Lesson l) -> l.getStudentGroup() != null && l.getStudentGroup().name != null
                        ? l.getStudentGroup().name : "")
                .thenComparing(l -> l.getTeacher() != null && l.getTeacher().name != null ? l.getTeacher().name : "")
                .thenComparing(l -> l.getSubject() != null ? l.getSubject() : "")
                .thenComparing(l -> l.id != null ? l.id : Long.MAX_VALUE));

        int placed = 0;
        int evicted = 0;
        // ── Direct pass: place each boxed lesson at its legal cell whose day has the deepest
        //    M20 hole (chronologically first among equals, see findDirectCell). ──
        for (Lesson lesson : boxed) {
            Timeslot chosen = findDirectCell(lesson, byTs, groupDaySlots, teacherDaily);
            if (chosen != null) {
                place(lesson, chosen, byTs, groupDaySlots, teacherDaily, moveTimeslot, nullRoom);
                placed++;
            }
        }

        // ── TASK-414 eviction pass: for each still-boxed lesson, borrow an occupied cell by
        //    relocating its single unpinned blocker to a fully-legal cell. Runs in the same
        //    repair call (and the post-compaction second repair) so compaction-freed cells
        //    are seen first by the direct pass above; eviction only touches residuals. ──
        for (Lesson lesson : boxed) {
            if (lesson.timeslot != null) continue; // direct pass already placed it
            EvictionMove move = findEviction(lesson, byTs, groupDaySlots, teacherDaily);
            if (move != null) {
                commitEviction(lesson, move, byTs, groupDaySlots, teacherDaily, moveTimeslot, nullRoom);
                placed++;
                evicted++;
            }
        }

        long conflictsAfter = StudentDayCompaction.countConflicts(schedule);
        return new Result(boxed.size(), placed, evicted, boxed.size() - placed, conflictsAfter - conflictsBefore);
    }

    /**
     * Direct pass: the legal cell for {@code lesson} whose day has the DEEPEST M20 hole,
     * chronologically first among equals. Returns null if no VRP cell passes all four
     * placement gates.
     *
     * <p><b>Ranking key</b> (TASK-419 follow-up A4): {@code (m20 marginal fill gain DESC,
     * getAvailableTimeslots() order ASC)}, the second key being the chronological VRP order
     * every search in this file uses, so the choice is total and stable. The old key was
     * "first day with ANY expected-hours deficit" - shape-blind, which stopped being good
     * enough the day {@link SchoolScheduleConstraintProvider#m20DeficitCost} became
     * progressive (marginal 1000, 1500, ... 4500): one hour is worth 4.5x more to a
     * 4-hours-short day than to a day one hour short, and taking the chronologically first
     * shallow day spends it at the cheapest possible rate.
     *
     * <p><b>Where it matters</b>: this repair also runs mid-pipeline ({@link CompactionPhase}),
     * where a local search still follows and could re-price the choice itself; the motivation
     * is the post-release staged path ({@code ScheduleService#runHoleRepair} and the repair
     * that precedes it), where NO timeslot local search follows and every placement is final.
     * Mid-pipeline the change is safe by construction: it only picks a different EQUALLY LEGAL
     * cell (identical gates, identical legality), never a cell the old order would have refused.
     */
    private static Timeslot findDirectCell(Lesson lesson, Map<Timeslot, List<Lesson>> byTs,
                                           Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                           Map<String, Integer> teacherDaily) {
        StudentGroup group = lesson.getStudentGroup();
        Teacher teacher = lesson.getTeacher();
        Integer groupCap = group != null ? group.maxHoursPerDay : null;
        int teacherCap = teacher != null && !teacher.pseudo ? teacher.getEffectiveMaxDailyHours() : 0;
        // One gain per DATE, not per cell: the whole day shares it, and no commit happens
        // inside this scan, so the cached value cannot go stale.
        Map<LocalDate, Integer> gainByDate = new HashMap<>();
        Timeslot best = null;
        int bestGain = Integer.MIN_VALUE;
        for (Timeslot ts : lesson.getAvailableTimeslotsFresh()) {       // (TASK-578: FRESH, stale-cache crash class)
            LocalDate date = ts.getDate();
            TreeMap<Integer, Boolean> daySlots = group != null && date != null
                    ? groupDaySlots.get(group.id + "@" + date) : null;
            if (!passesNonConflictPlacementGates(group, teacher, groupCap, teacherCap, ts, daySlots, teacherDaily,
                    false, false)) {
                continue;
            }
            if (!StudentDayCompaction.isSlotConflictFree(lesson, ts, byTs)) continue;
            Integer gain = gainByDate.get(date);
            if (gain == null) {
                gain = m20FillGain(group, date, daySlots);
                gainByDate.put(date, gain);
            }
            if (gain > bestGain) {   // strictly greater: chronological order breaks every tie
                bestGain = gain;
                best = ts;
            }
        }
        return best;
    }

    /**
     * TASK-414: find the best legal {@code (ts, O, ts2)} eviction for the still-boxed
     * {@code lesson}, or null. For each of L's VRP cells that passes L's H14/H17-arrival
     * gates, if exactly one unpinned occupant O blocks it, O is a candidate victim. Among
     * the candidates that CAN relocate (O's full gate set, including the new
     * departure-H17-safe gate) AND leave L placeable (swap-aware caps), pick the
     * LEAST-CONSTRAINED victim: the O with the MOST legal relocation cells (the counterpart
     * of most-constrained-first construction, we build from the constrained, we evict the
     * free). Ties break on chronological ts; the chosen ts2 is the first (chronological)
     * relocation that also satisfies L's caps. Both moves are validated here before the
     * caller commits either.
     */
    private static EvictionMove findEviction(Lesson lesson, Map<Timeslot, List<Lesson>> byTs,
                                             Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                             Map<String, Integer> teacherDaily) {
        StudentGroup group = lesson.getStudentGroup();
        Teacher teacher = lesson.getTeacher();
        Integer groupCap = group != null ? group.maxHoursPerDay : null;
        int teacherCap = teacher != null && !teacher.pseudo ? teacher.getEffectiveMaxDailyHours() : 0;
        EvictionMove best = null;
        int bestOptions = -1;
        for (Timeslot ts : lesson.getAvailableTimeslotsFresh()) {       // (TASK-578: FRESH, stale-cache crash class)
            LocalDate date = ts.getDate();
            if (date == null) continue;
            if (group != null && group.startDate != null && date.isBefore(group.startDate)) continue;  // H14
            TreeMap<Integer, Boolean> daySlots = group != null ? groupDaySlots.get(group.id + "@" + date) : null;
            if (!isH17Safe(daySlots, TimeslotSequence.slotIndexOf(ts))) continue;                       // H17 arrival
            Lesson blocker = singleUnpinnedBlocker(lesson, ts, byTs);
            if (blocker == null) continue; // free already, or blocked by 0/>1/pinned occupants
            // Blocker's relocation context: the departure gate (different-group only, since a
            // same-group blocker's slot is refilled by L) and the origin-day-with-slot-vacated
            // map used for different-group same-day arrival checks.
            StudentGroup bGroup = blocker.getStudentGroup();
            int originSlot = TimeslotSequence.slotIndexOf(ts);
            boolean sameGroup = StudentDayCompaction.sharesGroup(lesson, blocker);
            TreeMap<Integer, Boolean> originDay = bGroup != null ? groupDaySlots.get(bGroup.id + "@" + date) : null;
            if (!sameGroup && bGroup != null && !isH17SafeDeparture(originDay, originSlot)) continue;
            TreeMap<Integer, Boolean> originVacated = null;
            if (!sameGroup && originDay != null) {
                originVacated = new TreeMap<>(originDay);
                originVacated.remove(originSlot);
            }
            // Count O's legal relocation cells (its free alternatives) and remember the first
            // one that also keeps L placeable (swap-aware caps: L's count only rises unless O
            // is same-group/same-teacher AND relocates cross-day).
            boolean sameTeacher = StudentDayCompaction.sharesRealTeacher(lesson, blocker);
            int options = 0;
            Timeslot placeable = null;
            for (Timeslot cand : blocker.getAvailableTimeslotsFresh()) { // (TASK-578: FRESH, stale-cache crash class)
                if (!isLegalRelocation(blocker, cand, ts, date, sameGroup, originDay, originVacated,
                        groupDaySlots, teacherDaily, byTs)) continue;
                options++;
                if (placeable == null) {
                    // L's own caps AFTER the swap: only a CROSS-day relocation of a blocker
                    // that shares L's group/teacher frees a unit of that cap (a same-day
                    // relocation leaves O on the date, so L's arrival still adds one).
                    boolean crossDay = !date.equals(cand.getDate());
                    if (passesNonConflictPlacementGates(group, teacher, groupCap, teacherCap, ts, daySlots,
                            teacherDaily, sameGroup && crossDay, sameTeacher && crossDay)) {
                        placeable = cand;
                    }
                }
            }
            if (placeable == null) continue;  // O has alternatives but none leave L placeable
            if (options > bestOptions) {       // least-constrained victim: most alternatives wins
                bestOptions = options;
                best = new EvictionMove(ts, blocker, placeable);
            }
        }
        return best;
    }

    /**
     * The single unpinned occupant at {@code ts} that conflicts with {@code mover}
     * (shares its group or its non-pseudo teacher), or null if there is not exactly
     * one such blocker. A free cell, a cell blocked only by pinned lessons, or a cell
     * blocked by two+ occupants all return null: one-level eviction can only clear a
     * single unpinned blocker, and a cell already free needs no eviction (the direct
     * pass would have taken it).
     */
    // Package-private: shared with ExactFinisher (TASK-478 AC#3), whose depth-1 bump search
    // needs the same single-blocker predicate. Same widening discipline as isLegalRelocation.
    static Lesson singleUnpinnedBlocker(Lesson mover, Timeslot ts, Map<Timeslot, List<Lesson>> byTs) {
        List<Lesson> here = byTs.get(ts);
        if (here == null) return null;
        Lesson blocker = null;
        for (Lesson other : here) {
            if (other == mover) continue;
            // Same H1/H3 pair test as every other placement gate here (TASK-419 code review
            // unified the three near-copies); the COUNTING semantics below are this method's.
            if (!StudentDayCompaction.conflicts(mover, other)) continue;
            if (other.pinned) return null;   // a pinned blocker cannot be evicted
            if (blocker != null) return null; // more than one blocker, one level can't clear it
            blocker = other;
        }
        return blocker;
    }

    /**
     * TASK-414: true iff {@code occupant} can legally relocate from its origin cell to
     * {@code cand} (a move, not a fresh placement). Checks O's placement gates for the
     * candidate (H14; conflict-free; H11/H7 caps on a CROSS-day move only, since a
     * same-day move is count-neutral for O; H17-safe ARRIVAL) against the precomputed
     * origin context: {@code sameGroup} (L refills O's origin slot, so a same-day arrival
     * reads the full {@code originDay}; a different-group same-day arrival reads
     * {@code originVacated}, that day with O's slot removed). The DEPARTURE-H17-safe gate
     * is the caller's responsibility (checked once per blocker in {@link #findEviction},
     * before counting alternatives).
     *
     * <p>Package-private: shared with {@link ExactFinisher} (TASK-478 AC#3), whose
     * {@code findRelocationCell} calls this instead of re-deriving a relocation-aware
     * H17/cap gate from {@link #passesPlacementGates} (which is the FRESH-placement gate
     * and would double-count the mover at its origin on a same-day relocation). Same
     * sharing discipline as {@link #passesPlacementGates}, {@link #isDepartureSafe},
     * {@link #isH17Safe} and the index builders.
     */
    static boolean isLegalRelocation(Lesson occupant, Timeslot cand, Timeslot originTs,
                                             LocalDate originDate, boolean sameGroup,
                                             TreeMap<Integer, Boolean> originDay, TreeMap<Integer, Boolean> originVacated,
                                             Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                             Map<String, Integer> teacherDaily,
                                             Map<Timeslot, List<Lesson>> byTs) {
        if (cand.equals(originTs)) return false;
        LocalDate date2 = cand.getDate();
        if (date2 == null) return false;
        StudentGroup group = occupant.getStudentGroup();
        Teacher teacher = occupant.getTeacher();
        if (group != null && group.startDate != null && date2.isBefore(group.startDate)) return false;  // H14
        if (!StudentDayCompaction.isSlotConflictFree(occupant, cand, byTs)) return false;               // conflict-free
        Integer groupCap = group != null ? group.maxHoursPerDay : null;
        int teacherCap = teacher != null && !teacher.pseudo ? teacher.getEffectiveMaxDailyHours() : 0;
        TreeMap<Integer, Boolean> arrivalBefore;
        if (date2.equals(originDate)) {
            // Same-day: O stays on the date. Same-group -> L refills the slot, read the full
            // origin day; different-group -> O vacates the slot, read it vacated.
            arrivalBefore = sameGroup ? originDay : originVacated;
        } else {
            // Cross-day: read the destination day untouched; caps bind (same-day is neutral).
            arrivalBefore = group != null ? groupDaySlots.get(group.id + "@" + date2) : null;
            if (groupCap != null && groupCap > 0 && arrivalBefore != null && arrivalBefore.size() >= groupCap) return false;
            if (teacherCap > 0 && teacher != null
                    && teacherDaily.getOrDefault(teacher.id + "@" + date2, 0) >= teacherCap) return false;
        }
        return isH17Safe(arrivalBefore, TimeslotSequence.slotIndexOf(cand));  // H17 arrival
    }

    /**
     * Commit a direct placement: move, null the room, keep byTs / groupDaySlots /
     * teacherDaily live. Maps only grow here.
     */
    private static void place(Lesson lesson, Timeslot ts, Map<Timeslot, List<Lesson>> byTs,
                              Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                              Map<String, Integer> teacherDaily,
                              BiConsumer<Lesson, Timeslot> moveTimeslot, Consumer<Lesson> nullRoom) {
        StudentGroup group = lesson.getStudentGroup();
        Teacher teacher = lesson.getTeacher();
        LocalDate date = ts.getDate();
        moveTimeslot.accept(lesson, ts);
        nullRoom.accept(lesson);
        byTs.computeIfAbsent(ts, k -> new ArrayList<>()).add(lesson);
        if (group != null) {
            groupDaySlots.computeIfAbsent(group.id + "@" + date, k -> new TreeMap<>())
                    .put(TimeslotSequence.slotIndexOf(ts), false);
        }
        if (teacher != null && !teacher.pseudo) {
            teacherDaily.merge(teacher.id + "@" + date, 1, Integer::sum);
        }
    }

    /**
     * TASK-414: commit the atomic two-move chain. First relocate {@code O} from
     * {@code ts} (L's target cell) to {@code ts2}, REMOVING O's origin entries from
     * all three maps (the new shrink case) before adding its destination entries,
     * then place L at {@code ts}. The move was already proven legal against the live
     * maps by {@link #findEviction}/{@link #findRelocationFor}; commits follow the
     * validated order so L's placement never fails after O has moved.
     */
    private static void commitEviction(Lesson lesson, EvictionMove move, Map<Timeslot, List<Lesson>> byTs,
                                       Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                       Map<String, Integer> teacherDaily,
                                       BiConsumer<Lesson, Timeslot> moveTimeslot, Consumer<Lesson> nullRoom) {
        Timeslot originTs = move.targetCell();     // the cell L will take = O's current cell
        Lesson occupant = move.blocker();
        Timeslot destTs = move.relocationCell();
        LocalDate originDate = originTs.getDate();
        LocalDate destDate = destTs.getDate();
        int originSlot = TimeslotSequence.slotIndexOf(originTs);
        int destSlot = TimeslotSequence.slotIndexOf(destTs);

        // ── Relocate O: ts -> ts2 ──
        moveTimeslot.accept(occupant, destTs);
        nullRoom.accept(occupant);
        List<Lesson> originList = byTs.get(originTs);
        if (originList != null) originList.remove(occupant);
        byTs.computeIfAbsent(destTs, k -> new ArrayList<>()).add(occupant);

        StudentGroup oGroup = occupant.getStudentGroup();
        if (oGroup != null) {
            // Remove O's origin slot (shrink). For a same-day move the origin and
            // destination day maps are the same object, so remove-then-add is in-place.
            TreeMap<Integer, Boolean> originDay = groupDaySlots.get(oGroup.id + "@" + originDate);
            if (originDay != null) originDay.remove(originSlot);
            if (originDate.equals(destDate)) {
                originDay.put(destSlot, false);   // same map object, already had originSlot removed
            } else {
                groupDaySlots.computeIfAbsent(oGroup.id + "@" + destDate, k -> new TreeMap<>())
                        .put(destSlot, false);
            }
        }
        Teacher oTeacher = occupant.getTeacher();
        if (oTeacher != null && !oTeacher.pseudo) {
            // Same-day move: O stays on the date (different slot), so its teacher's daily
            // count is unchanged, a no-op, mirroring the group map's in-place remove+add
            // above. Decrementing without re-incrementing (the old form) left the count 1
            // below reality, letting a later eviction pass the H7 gate on a teacher already
            // at cap (TASK-414 review: silent H7 violation invisible to conflictsAdded).
            if (!originDate.equals(destDate)) {
                decrement(teacherDaily, oTeacher.id + "@" + originDate);
                teacherDaily.merge(oTeacher.id + "@" + destDate, 1, Integer::sum);
            }
        }

        // ── Place L at ts (O's freed cell) ──
        place(lesson, originTs, byTs, groupDaySlots, teacherDaily, moveTimeslot, nullRoom);
    }

    /**
     * Decrement a count map entry, removing it when it reaches zero (keeps maps clean).
     * Package-private: {@link HoleRepair}'s import maintains the same H7 daily-count map and
     * must shrink it the same way (TASK-419 code review - it was a byte-identical copy).
     */
    static void decrement(Map<String, Integer> counts, String key) {
        Integer v = counts.get(key);
        if (v == null) return;
        if (v <= 1) counts.remove(key);
        else counts.put(key, v - 1);
    }

    /**
     * The per-(group, date) {@code slotIndex -> AUTHORITATIVELY pinned} map of a lesson
     * population - the shape {@link #h17DayPenalty} and every H17 gate here read, keyed
     * {@code groupId@date}. The group's daily hour count IS the map's size (one same-group
     * lesson per slot in a conflict-free population). Shared with {@link StagedPinPolicy} and
     * {@link HoleRepair} so the three cannot drift on the encoding (TASK-419).
     *
     * <p><b>The flag is {@link BaseConstraintProvider#isAuthoritativePin}, NOT the raw
     * {@code pinned} field</b> (TASK-419 review): H17's own break-level exemption is written
     * against the authoritative pins, so a break between two lessons the staged cycle merely
     * PROMOTED is penalized by the score. Storing the raw flag here would make every gate in
     * this file read such a break as exempt while the score charges for it - the gate and the
     * score disagreeing during stage B/C, which shows up as placeable cells refused (a break
     * the gate thinks it may open) and demotions missed or spent on nothing. Outside the
     * staged cycle the promotion registry is empty and the two semantics are identical, so
     * this is a no-op for every ordinary solve.
     */
    static Map<String, TreeMap<Integer, Boolean>> buildGroupDaySlots(List<Lesson> lessons) {
        Map<String, TreeMap<Integer, Boolean>> groupDaySlots = new HashMap<>();
        for (Lesson l : lessons) {
            if (l.timeslot == null) continue;
            LocalDate date = l.timeslot.getDate();
            if (date == null) continue;
            StudentGroup g = l.getStudentGroup();
            if (g == null) continue;
            groupDaySlots.computeIfAbsent(g.id + "@" + date, k -> new TreeMap<>())
                    .put(TimeslotSequence.slotIndexOf(l.timeslot), BaseConstraintProvider.isAuthoritativePin(l));
        }
        return groupDaySlots;
    }

    /** Per-(non-pseudo teacher, date) placed-hour count, keyed {@code teacherId@date} - the H7 read. */
    static Map<String, Integer> buildTeacherDaily(List<Lesson> lessons) {
        Map<String, Integer> teacherDaily = new HashMap<>();
        for (Lesson l : lessons) {
            if (l.timeslot == null) continue;
            LocalDate date = l.timeslot.getDate();
            if (date == null) continue;
            Teacher t = l.getTeacher();
            if (t != null && !t.pseudo) {
                teacherDaily.merge(t.id + "@" + date, 1, Integer::sum);
            }
        }
        return teacherDaily;
    }

    /**
     * The SHARED placement gate (TASK-419): true iff placing {@code lesson} at {@code ts}
     * passes H14 + H11 + H7 + H17-safe ARRIVAL, resolving the caps and the day map from the
     * lesson itself. This is the same gate set {@link #findDirectCell} applies, exposed so
     * {@link StagedPinPolicy}'s deadlock test and {@link HoleRepair}'s import can ask the
     * exact question "would BoxRepair take this cell" instead of re-deriving a near-copy of
     * it - a near-copy is precisely what let stage C read a boxed lesson as placeable on a
     * cell BoxRepair's own H17 gate refuses (the A' class of the TASK-419 discriminator).
     * Conflict-free (H1/H3) stays the caller's call, see the note below.
     */
    static boolean passesPlacementGates(Lesson lesson, Timeslot ts,
                                        Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                        Map<String, Integer> teacherDaily) {
        return passesSwapAwarePlacementGates(lesson, ts, null, groupDaySlots, teacherDaily);
    }

    /**
     * The SWAP-AWARE form of {@link #passesPlacementGates} (TASK-419): the same H14 + H17
     * arrival gates, but the H11/H7 DAILY CAPS are waived on whichever side the swap frees -
     * the group cap when {@code occupant} is a same-group lesson, the teacher cap when it
     * shares {@code lesson}'s non-pseudo teacher. {@code occupant == null} means "no swap",
     * i.e. exactly the direct-placement gate.
     *
     * <p><b>Why the caller needs it</b> (the F2 finding of the TASK-419 review): asking the
     * direct-placement question about a cell whose occupant is about to LEAVE reads a day that
     * is already at {@code maxHoursPerDay} as full, so a saturated day's cells are discarded -
     * exactly the cells a demotion exists to open. {@link StagedPinPolicy}'s blocker search is
     * the caller: there the occupant is a promoted pin that stops being immovable, so its hour
     * is the one the boxed lesson would take.
     *
     * <p><b>The optimism is bounded and deliberate.</b> Waiving the cap assumes the freed
     * occupant ends up on ANOTHER date (a same-day relocation would leave the day's count
     * unchanged, so the arrival would still breach the cap - which is why {@link
     * #findEviction}, where the destination IS known, waives it only for a cross-day
     * relocation, via the same two flags). The demotion has no destination to inspect, and
     * being optimistic there costs nothing: it only decides whether a pin is WORTH releasing,
     * while the placement that follows still has to pass this same gate for real.
     */
    static boolean passesSwapAwarePlacementGates(Lesson lesson, Timeslot ts, Lesson occupant,
                                                 Map<String, TreeMap<Integer, Boolean>> groupDaySlots,
                                                 Map<String, Integer> teacherDaily) {
        StudentGroup group = lesson.getStudentGroup();
        Teacher teacher = lesson.getTeacher();
        LocalDate date = ts.getDate();
        Integer groupCap = group != null ? group.maxHoursPerDay : null;
        int teacherCap = teacher != null && !teacher.pseudo ? teacher.getEffectiveMaxDailyHours() : 0;
        TreeMap<Integer, Boolean> daySlots = group != null && date != null
                ? groupDaySlots.get(group.id + "@" + date) : null;
        boolean groupCountNeutral = occupant != null && StudentDayCompaction.sharesGroup(lesson, occupant);
        boolean teacherCountNeutral = occupant != null && StudentDayCompaction.sharesRealTeacher(lesson, occupant);
        return passesNonConflictPlacementGates(group, teacher, groupCap, teacherCap, ts, daySlots, teacherDaily,
                groupCountNeutral, teacherCountNeutral);
    }

    /**
     * The DEPARTURE half of the shared gate: true iff removing {@code lesson} from its own
     * (group, date) does not open a penalized H17 break ({@link #isH17SafeDeparture}). A
     * boxed lesson (no timeslot) has no departure and trivially passes.
     */
    static boolean isDepartureSafe(Lesson lesson, Map<String, TreeMap<Integer, Boolean>> groupDaySlots) {
        if (lesson.timeslot == null) return true;
        StudentGroup group = lesson.getStudentGroup();
        LocalDate date = lesson.timeslot.getDate();
        if (group == null || date == null) return true;
        return isH17SafeDeparture(groupDaySlots.get(group.id + "@" + date),
                TimeslotSequence.slotIndexOf(lesson.timeslot));
    }

    /**
     * True iff placing an UNPINNED lesson at {@code ts} passes the non-conflict placement
     * gates: H14 (group start date), H11 (group daily cap), H7 (teacher daily cap, pseudo
     * exempt) and H17-safe ARRIVAL. Conflict-free (H1/H3) is checked separately by the
     * caller, the direct pass refuses a conflicted cell, while the eviction pass seeks a
     * cell that IS conflicted (its single unpinned blocker is the eviction candidate). The
     * caller passes the already-fetched {@code daySlots} so the direct pass can reuse it
     * for its M20 day-ranking key (one map lookup per cell, not two).
     *
     * <p>The two {@code *CountNeutral} flags are the SWAP form (TASK-419): true means the
     * lesson vacating the target cell already accounts for that cap's unit on this date, so
     * the arrival does not raise the count and the cap must not be re-charged. Both false is
     * a fresh placement. The single place either cap is evaluated - {@link #findEviction},
     * {@link #passesSwapAwarePlacementGates} and {@link ExactFinisher}'s bump-arrival gate all
     * come through here rather than re-deriving the arithmetic. Package-private for that last
     * caller (TASK-478 AC#3), same widening discipline as {@link #isLegalRelocation} and
     * {@link #singleUnpinnedBlocker}.
     */
    static boolean passesNonConflictPlacementGates(StudentGroup group, Teacher teacher,
                                                           Integer groupCap, int teacherCap, Timeslot ts,
                                                           TreeMap<Integer, Boolean> daySlots,
                                                           Map<String, Integer> teacherDaily,
                                                           boolean groupCountNeutral, boolean teacherCountNeutral) {
        LocalDate date = ts.getDate();
        if (date == null) return false;
        if (group != null && group.startDate != null && date.isBefore(group.startDate)) return false;
        if (!groupCountNeutral && groupCap != null && groupCap > 0
                && daySlots != null && daySlots.size() >= groupCap) return false;
        if (!teacherCountNeutral && teacherCap > 0 && teacher != null
                && teacherDaily.getOrDefault(teacher.id + "@" + date, 0) >= teacherCap) return false;
        return isH17Safe(daySlots, TimeslotSequence.slotIndexOf(ts));
    }

    /**
     * The M20 medium one more hour would save on {@code (group, date)} - the day-ranking key
     * of {@link #findDirectCell}, delegated whole to {@link
     * SchoolScheduleConstraintProvider#m20MarginalFillGain} so the repair and the score read
     * ONE quota definition (calendar cap, H11 cap, the TASK-408 no-session rule and the G14
     * exemption all included). Replaces the TASK-406 raw {@code getExpectedHoursRow}
     * comparison, which knew none of those rules and could not see depth at all.
     *
     * <p>Package-private: {@link SurplusRebalance} ranks its TARGET days on the very same
     * question (TASK-422), and a second copy of the occupancy/placedTotal split is exactly the
     * drift these shared primitives exist to prevent.
     */
    static int m20FillGain(StudentGroup group, LocalDate date,
                                   TreeMap<Integer, Boolean> daySlots) {
        if (group == null || date == null) return 0;
        return SchoolScheduleConstraintProvider.m20MarginalFillGain(group, date,
                m20Occupancy(daySlots), daySlots == null ? 0 : daySlots.size());
    }

    /**
     * M20's own {@code totalHours} for a day map built by {@link #buildGroupDaySlots}: the
     * placed hours that are NOT authoritative pins (the map's {@code false} values), which is
     * exactly the population {@code horizontallyCompact} groups and counts. Shared with
     * {@link HoleRepair}, which prices a donor's source day on the same basis.
     */
    static int m20Occupancy(TreeMap<Integer, Boolean> daySlots) {
        if (daySlots == null) return 0;
        int occupancy = 0;
        for (Boolean authoritativePin : daySlots.values()) {
            if (!Boolean.TRUE.equals(authoritativePin)) occupancy++;
        }
        return occupancy;
    }

    /**
     * True iff placing an UNPINNED lesson at {@code slotIndex} does not increase the
     * group's day H17 penalty ({@link #h17DayPenalty} before vs after the simulated
     * placement). An exact-delta gate, not an adjacency heuristic — see the class
     * javadoc's H17-safe bullet for the pinned-exempt-gap counterexample it exists for.
     */
    static boolean isH17Safe(TreeMap<Integer, Boolean> daySlots, int slotIndex) {
        if (daySlots == null || daySlots.isEmpty()) return true;
        int before = h17DayPenalty(daySlots);
        TreeMap<Integer, Boolean> after = new TreeMap<>(daySlots);
        after.put(slotIndex, false);
        return h17DayPenalty(after) <= before;
    }

    /**
     * TASK-414: the mirror of {@link #isH17Safe} for a REMOVAL, true iff removing the
     * unpinned occupant at {@code slotIndex} does not increase the day's H17 penalty
     * ({@link #h17DayPenalty} before vs after the simulated removal). Holds for an
     * edge-of-run lesson (the run shrinks, no new break) and for a day-emptying removal
     * (penalty collapses to 0); refuses a mid-run lesson whose removal would open a new
     * penalized break. A break whose remaining bounding lessons are BOTH AUTHORITATIVE pins
     * scores 0 regardless (task-350 exemption), so removing a lesson that leaves such a gap
     * is non-increasing and allowed, that gap is H17-legal, never a hard violation.
     */
    static boolean isH17SafeDeparture(TreeMap<Integer, Boolean> daySlots, int slotIndex) {
        if (daySlots == null || daySlots.isEmpty() || !daySlots.containsKey(slotIndex)) return true;
        int before = h17DayPenalty(daySlots);
        TreeMap<Integer, Boolean> after = new TreeMap<>(daySlots);
        after.remove(slotIndex);
        return h17DayPenalty(after) <= before;
    }

    /**
     * The day's H17 penalty for a {@code slotIndex -> authoritatively pinned} map (see
     * {@link #buildGroupDaySlots}), mirroring the constraint's break-level pinned exemption
     * (task-350): each break between consecutive occupied slots costs its empty width,
     * EXCEPT when the lessons on both sides are AUTHORITATIVE pins ({@link
     * BaseConstraintProvider#isAuthoritativePin}, the same gate {@code verticallyCompact}
     * applies) - a promoted pin is not one.
     *
     * <p><b>Also M15's break shape</b> (TASK-422): {@code
     * sameDaySameSubjectShouldBeConsecutive} penalizes exactly the same breaks, with the same
     * both-authoritative-pins exemption, on the (group, subject) SLICE of a day instead of the
     * whole day - so {@link SurplusRebalance} feeds that slice to the two gates ABOVE ({@link
     * #isH17Safe} and {@link #isH17SafeDeparture}, never to this function directly) rather than
     * writing a second break scanner. The unit stays slot width, which is exact for M15 too:
     * every slot is one hour and the constraint subtracts the lunch break, so its gap-minutes
     * are {@code 60 x} the width counted here.
     */
    static int h17DayPenalty(TreeMap<Integer, Boolean> daySlots) {
        int penalty = 0;
        Map.Entry<Integer, Boolean> prev = null;
        for (Map.Entry<Integer, Boolean> e : daySlots.entrySet()) {
            if (prev != null) {
                int width = e.getKey() - prev.getKey() - 1;
                if (width > 0 && !(prev.getValue() && e.getValue())) {
                    penalty += width;
                }
            }
            prev = e;
        }
        return penalty;
    }

    /** A validated {@code (target cell ts, blocker O, relocation cell ts2)} triple for one eviction. */
    private record EvictionMove(Timeslot targetCell, Lesson blocker, Timeslot relocationCell) {
    }
}
