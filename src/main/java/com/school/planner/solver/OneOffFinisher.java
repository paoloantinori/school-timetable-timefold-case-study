package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;

/**
 * TASK-574: the physical one-off closer. Post-B-ROOM deterministic pass, sibling of
 * {@link RoomTailFinisher} (which closes ROOM gaps but can never close a
 * tag-collision roomless lesson: it moves rooms, not timeslots). Three move
 * families, each driven by the tiny residual state the staged cycle leaves behind:
 *
 * <ul>
 * <li><b>A, H7 overload relief</b>: a teacher-day over {@code effectiveMaxDailyHours}
 *     gives up ONE unpinned hour, moved to a compatible slot (teacher/group free,
 *     within the lesson's own VRP range, room resolvable). Candidates are ordered
 *     edge-of-the-group's-day first so the move does not open an H17 gap.</li>
 * <li><b>B, H29 roomless relocation</b>: a placed real-teacher lesson with no room
 *     and a required tag (the only meccanica lab is taken at its slot) moves in
 *     TIME to a slot where a tagged room is free - the move the golden always
 *     makes (it never co-locates two meccanica labs).</li>
 * <li><b>C, H33 floor fill</b>: a group-day under its hard floor pulls hours from a
 *     same-group surplus day; donor lessons are taken from the edge of the donor
 *     block (the first hour never moves: H31) and land adjacent to the target
 *     day's existing block (H17-safe by construction).</li>
 * </ul>
 *
 * <p>Improve-or-noop is the CALLER's oracle (every move here only claims local
 * legality: teacher free, group free, room resolvable, VRP range); the wrapper
 * scores before/after and reverts the whole pass on any lexicographic regression,
 * exactly like every deterministic sibling. The bench
 * ({@code PureOneOffCheckpointTest}, TASK-672 re-freeze) pins the frontier
 * delivery checkpoint: on a state with zero H7/H29/H33 targets the pass must
 * be a no-op (zero moves, score unchanged); the curative arm with injected
 * targets is TASK-673.
 *
 * <p>Flag {@value #FLAG}, standard opt-out form (null/absent -> ON, only the
 * literal "false" disarms).
 */
public final class OneOffFinisher {

    /** DIAG (mech, laptop round 17): the dst of the resolveRoom call being diagnosed. */
    private static final ThreadLocal<String> lastDst = ThreadLocal.withInitial(() -> "?");

    private static final Logger LOG = Logger.getLogger(OneOffFinisher.class);

    public static final String FLAG = "planner.construction.oneoff-finisher.enabled";

    public static boolean enabled() {
        String v = System.getProperty(FLAG);
        return v == null || Boolean.parseBoolean(v);
    }

    /** Counters per family, for the log line and the bench. */
    public record Result(int overloadsRelieved, int roomlessRelocated, int floorFills, int movesRejected) {
        public static Result noop() {
            return new Result(0, 0, 0, 0);
        }

        public int movesApplied() {
            return overloadsRelieved + roomlessRelocated + floorFills;
        }
    }

    private OneOffFinisher() {
    }

    public static Result repair(SchoolSchedule schedule,
                                BiConsumer<Lesson, Timeslot> moveTimeslot,
                                BiConsumer<Lesson, Room> moveRoom) {
        if (!enabled() || schedule.getTimeslots() == null || schedule.getLessons() == null) {
            return Result.noop();
        }
        Index idx = new Index(schedule);
        int a = relieveOverloads(schedule, idx, moveTimeslot, moveRoom);
        int b = relocateRoomless(schedule, idx, moveTimeslot, moveRoom);
        int c = fillFloors(schedule, idx, moveTimeslot, moveRoom);
        Result r = new Result(a, b, c, idx.rejected);
        LOG.infof("One-off finisher (TASK-574): overload relief %d, roomless relocation %d,"
                        + " floor fill %d, candidates rejected %d",
                a, b, c, idx.rejected);
        return r;
    }

    // ── Family A: H7 overload relief ────────────────────────────────────────────

    private static int relieveOverloads(SchoolSchedule schedule, Index idx,
                                        BiConsumer<Lesson, Timeslot> moveTimeslot,
                                        BiConsumer<Lesson, Room> moveRoom) {
        int relieved = 0;
        // One pass over the (tiny) overload set; re-derive after each move: the index shifts.
        for (int guard = 0; guard < 12; guard++) {
            Map<com.school.planner.domain.Teacher, Map<LocalDate, Integer>> over =
                    idx.teacherDayOverloads();
            var hit = over.entrySet().stream().findFirst();
            if (hit.isEmpty()) {
                break;
            }
            var teacher = hit.get().getKey();
            LocalDate date = hit.get().getValue().keySet().iterator().next();
            List<Lesson> dayLessons = idx.lessonsOf(teacher, date).stream()
                    .filter(l -> !l.pinned)
                    .sorted(Comparator.comparingInt(l -> idx.edgeRank(l)))
                    .collect(Collectors.toList());
            boolean moved = false;
            for (Lesson l : dayLessons) {
                if (tryMove(l, idx, moveTimeslot, moveRoom, null)) {
                    moved = true;
                    relieved++;
                    break;
                }
            }
            if (!moved) {
                LOG.infof("One-off finisher: overload of %s on %s has no movable unpinned hour, skipped",
                        teacher.name, date);
                idx.skipOverload(teacher, date);
            }
        }
        return relieved;
    }

    // ── Family B: H29 roomless relocation ───────────────────────────────────────

    private static int relocateRoomless(SchoolSchedule schedule, Index idx,
                                        BiConsumer<Lesson, Timeslot> moveTimeslot,
                                        BiConsumer<Lesson, Room> moveRoom) {
        int relocated = 0;
        List<Lesson> roomless = schedule.getLessons().stream()
                .filter(l -> l.timeslot != null && l.room == null)
                .filter(l -> l.course != null && l.course.teacher != null && !l.course.teacher.pseudo)
                .collect(Collectors.toList());
        for (Lesson l : roomless) {
            if (tryMove(l, idx, moveTimeslot, moveRoom, null)) {
                relocated++;
            } else if (extractAndCompact(l, idx, moveTimeslot, moveRoom)) {
                // v2: the roomless lesson sits mid-block (v1's tail-only departure
                // rejected it): extract it and slide the group's remaining block back
                // one grid slot, closing the hole - the EDF move, TASK-560 family.
                relocated++;
            } else if (chainFreeTheTagRoom(l, idx, moveTimeslot, moveRoom)) {
                // v3 (round-17 two-stage chain): the tag room is saturated in the
                // whole range, but its occupant block can slide BACK (the golden's own
                // shape on the shared day): free the occupant's slots first, then the
                // roomless lesson takes the room at ITS OWN current slot. No move for l.
                relocated++;
            } else {
                idx.rejected++;
                // DIAG (mech, laptop round 17): the roomless lesson's own funnel.
                {
                    // diagnosis log reads FRESH to match the funnel it explains (the
                    // tryMove/extractAndCompact guards below read Fresh: a cached count
                    // would attribute rejections to a range the code no longer consults)
                    List<Timeslot> r = l.getAvailableTimeslotsFresh();
                    long rr = r == null ? 0 : r.stream().filter(ts -> ts != l.timeslot).count();
                    long tf = r == null ? 0 : r.stream().filter(ts -> ts != l.timeslot)
                            .filter(ts -> !idx.teacherBusy(l.course.teacher, ts)).count();
                    long gf = r == null ? 0 : r.stream().filter(ts -> ts != l.timeslot)
                            .filter(ts -> !idx.teacherBusy(l.course.teacher, ts))
                            .filter(ts -> !idx.groupBusy(l.course.studentGroup, ts)).count();
                    long adj = r == null ? 0 : r.stream().filter(ts -> ts != l.timeslot)
                            .filter(ts -> !idx.teacherBusy(l.course.teacher, ts))
                            .filter(ts -> !idx.groupBusy(l.course.studentGroup, ts))
                            .filter(ts -> idx.landingAdjacent(l.course.studentGroup, ts)).count();
                    long qs = r == null ? 0 : r.stream().filter(ts -> ts != l.timeslot)
                            .filter(ts -> !idx.teacherBusy(l.course.teacher, ts))
                            .filter(ts -> !idx.groupBusy(l.course.studentGroup, ts))
                            .filter(ts -> idx.landingAdjacent(l.course.studentGroup, ts))
                            .filter(ts -> idx.landingQuotaSafe(l.course.studentGroup, ts)).count();
                    long h32 = r == null ? 0 : r.stream().filter(ts -> ts != l.timeslot)
                            .filter(ts -> !idx.teacherBusy(l.course.teacher, ts))
                            .filter(ts -> !idx.groupBusy(l.course.studentGroup, ts))
                            .filter(ts -> idx.landingAdjacent(l.course.studentGroup, ts))
                            .filter(ts -> idx.landingQuotaSafe(l.course.studentGroup, ts))
                            .filter(ts -> idx.moveH32Neutral(l, ts)).count();
                    LOG.infof("DIAG roomless funnel: lesson=%s range=%d teacherFree=%d groupFree=%d adjacent=%d quotaSafe=%d h32neutral=%d (04/11 14:30 in range: %s)",
                            l.id, rr, tf, gf, adj, qs, h32,
                            r == null ? "null-range" : String.valueOf(r.stream().anyMatch(ts ->
                                    "2026-11-04".equals(ts.getDate().toString())
                                            && ts.startDateTime.toLocalTime().toString().equals("14:30"))));
                }
                LOG.infof("One-off finisher: roomless %s (%s, %s) has no free tagged slot, skipped",
                        l.course.subject, l.course.teacher.name, l.course.studentGroup.name);
            }
        }
        return relocated;
    }


    /**
     * v3 (round-17): two-stage chain for a tag-saturated roomless lesson. Stage 1:
     * the tag-matching room's occupant block at (and after) the lesson's own slot
     * slides BACK in time (repeated previousGridSlot steps), the golden's own shape on
     * the one shared day (G04 10-12 -> 8-10). Backward slides are H31-safe by
     * direction (towards the day start). Stage 2: with the room free at the lesson's
     * CURRENT slot, the lesson simply takes it: no timeslot move, the H29 closes.
     * Bounded attempts; every slide verified on the post state (teacher free, group
     * free, the room itself follows the block).
     */
    private static boolean chainFreeTheTagRoom(Lesson l, Index idx,
                                               BiConsumer<Lesson, Timeslot> moveTimeslot,
                                               BiConsumer<Lesson, Room> moveRoom) {
        if (l == null || l.timeslot == null || l.course == null || l.course.studentGroup == null
                || l.room != null) {
            return false;
        }
        var tags = l.course.requiredRoomTags;
        if (tags == null || tags.isEmpty()) {
            return false;
        }
        // round-18 form: the block to move is the CONTIGUOUS same-group run of ANOTHER
        // group sitting in a tag-matching room and covering l's own slot (v3's occupant
        // set wrongly included G13's own tail and slid it too). If no other-group run
        // covers l's slot in a tagged room, nothing to chain.
        Lesson occupantAtSlot = null;
        Room tagRoom = null;
        for (Lesson x : idx.schedule.getLessons()) {
            if (x == l || x.timeslot == null || x.room == null || x.pinned) {
                continue;
            }
            if (!x.timeslot.equals(l.timeslot)) {
                continue;
            }
            if (x.room.tags == null || !x.room.tags.containsAll(tags)) {
                continue;
            }
            if (x.course.studentGroup != null
                    && !x.course.studentGroup.equals(l.course.studentGroup)) {
                occupantAtSlot = x;
                tagRoom = x.room;
                break;
            }
        }
        if (occupantAtSlot == null || tagRoom == null) {
            return false;
        }
        // the contiguous same-group run containing the occupant (same date, adjacent
        // grid slots, any room): that is the school's lab block (G04 10-12).
        List<Lesson> block = idx.contiguousRun(occupantAtSlot);
        block.sort(Comparator.comparing(x -> x.timeslot.startDateTime));
        // candidate destinations: BLOCK TRANSLATES (leap allowed) over the date's grid
        // slots, k steps back and forward (k=1..3): the whole run moves together.
        List<Timeslot> dayGrid = idx.gridSlotsOf(l.timeslot.getDate());
        int base = dayGrid.indexOf(block.get(0).timeslot);
        if (base < 0) {
            return false;
        }
        for (int k : new int[]{-3, -2, -1, 1, 2, 3}) {
            int destBase = base + k;
            if (destBase < 0 || destBase + block.size() > dayGrid.size()) {
                continue;
            }
            boolean ok = true;
            List<Timeslot> dests = new ArrayList<>();
            for (int i = 0; i < block.size(); i++) {
                Timeslot dst = dayGrid.get(destBase + i);
                dests.add(dst);
                Lesson x = block.get(i);
                // TASK-578 residue (found diagnosing the v10 cycle's two remaining
                // assertValueRange): this chain move translates a WHOLE block on the
                // day grid and never consulted any value range - the fourth placement
                // path outside the Fresh migration. Per-slot legality, same as every
                // migrated guard.
                if (!x.isSlotLegalFresh(dst)) {
                    ok = false;
                    break;
                }
                // post-state per BLOCK: teacher/group free excluding the whole block
                if (idx.teacherBusyExcludingSet(block, x, dst)
                        || idx.groupBusyExcludingSet(block, x, dst)) {
                    ok = false;
                    break;
                }
                // the room: free at dst for the block's own room use (the block keeps
                // its rooms; other tagged-room users - including G13's 13:00 - count)
                boolean roomTaken = idx.schedule.getLessons().stream().anyMatch(y ->
                        !block.contains(y) && RoomAvailability.occupies(y, x.room, dst));
                if (roomTaken) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            // one more condition: the move must FREE l's slot: the block no longer
            // covers l.timeslot, and the room at l.timeslot is free of other groups.
            boolean stillCovers = dests.stream().anyMatch(t -> t.equals(l.timeslot));
            boolean othersAtLSlot = idx.schedule.getLessons().stream().anyMatch(y ->
                    !block.contains(y) && y != l && y.room != null
                    && y.timeslot.equals(l.timeslot)
                    && y.room.tags != null && y.room.tags.containsAll(tags));
            if (stillCovers || othersAtLSlot) {
                continue;
            }
            // apply the block translate (rooms unchanged)
            for (int i = 0; i < block.size(); i++) {
                Lesson x = block.get(i);
                Timeslot dst = dests.get(i);
                Timeslot oldTs = x.timeslot;
                Room oldRoom = x.room;
                moveTimeslot.accept(x, dst);
                moveRoom.accept(x, x.room);
                idx.apply(x, oldTs, oldRoom, dst, x.room);
            }
            moveRoom.accept(l, tagRoom);
            idx.apply(l, l.timeslot, null, l.timeslot, tagRoom);
            LOG.infof("One-off finisher v4: block-translate %s (%d lessons of %s) by %d slots,"
                            + " freed %s for the roomless %s at %s",
                    tagRoom.name, block.size(),
                    block.get(0).course.studentGroup.name, k,
                    tagRoom.name, l.course.subject, l.timeslot.startDateTime);
            return true;
        }
        return false;
    }

    // ── Family C: H33 floor fill ────────────────────────────────────────────────

    private static int fillFloors(SchoolSchedule schedule, Index idx,
                                  BiConsumer<Lesson, Timeslot> moveTimeslot,
                                  BiConsumer<Lesson, Room> moveRoom) {
        int fills = 0;
        for (int guard = 0; guard < 12; guard++) {
            var under = idx.underFloorDays();
            if (under.isEmpty()) {
                break;
            }
            var e = under.entrySet().iterator().next();
            var group = e.getKey();
            LocalDate date = e.getValue().get(0);
            int deficit = idx.floorDeficit(group, date);
            boolean progressed = false;
            for (int k = 0; k < deficit && k < 4; k++) {
                Lesson donor = idx.bestDonor(group, date);
                if (donor == null) {
                    break;
                }
                if (tryMove(donor, idx, moveTimeslot, moveRoom, date)
                        || extractAndCompact(donor, idx, moveTimeslot, moveRoom, date)) {
                    fills++;
                    progressed = true;
                } else {
                    break;
                }
            }
            if (!progressed) {
                idx.skipUnderFloor(group, date);
            }
        }
        return fills;
    }


    /**
     * v2 mid-block extraction + compaction (the EDF/TASK-560 move, for the scarcity
     * wall of round 15): relocate {@code l} to the first legal slot (optionally
     * restricted to {@code targetDate}) with a NON-tail departure, then slide the
     * same-group lessons that followed it in its day BACK one grid slot each, closing
     * the hole. Atomic by staging: the full move plan (extraction + slides) is
     * computed and verified against the POST state, then applied. H31: the day's
     * first lesson never slides (the extractor only moves followers of l, and l is
     * never the first when the day has a block). H32: same day, same count: neutral.
     */
    private static boolean extractAndCompact(Lesson l, Index idx,
                                             BiConsumer<Lesson, Timeslot> moveTimeslot,
                                             BiConsumer<Lesson, Room> moveRoom) {
        return extractAndCompact(l, idx, moveTimeslot, moveRoom, null);
    }

    private static boolean extractAndCompact(Lesson l, Index idx,
                                             BiConsumer<Lesson, Timeslot> moveTimeslot,
                                             BiConsumer<Lesson, Room> moveRoom,
                                             LocalDate targetDate) {
        if (l == null || l.timeslot == null || l.course == null || l.course.studentGroup == null) {
            return false;
        }
        if (idx.isDayFirst(l) || idx.isDayTail(l)) {
            return false; // first hour never moves (H31); tail handled by plain tryMove
        }
        List<Timeslot> range = l.getAvailableTimeslotsFresh();         // (TASK-578: FRESH, stale-cache crash class)
        // 1) the slide plan: same-group followers of l on its day, each back one
        //    real grid slot (the greatest existing timeslot of the date strictly
        //    before its current one - the one l vacates or an earlier one).
        //    Hoisted BEFORE the candidate stream (/simplify + code-review): the
        //    plan depends only on the followers' current slots, never on the
        //    candidates, so a doomed plan must not pay the 8-filter stream first.
        List<Lesson> followers = idx.followersOf(l);
        java.util.Map<Lesson, Timeslot> slides = new HashMap<>();
        for (Lesson f : followers) {
            Timeslot back = idx.previousGridSlot(f.timeslot);
            // TASK-577 fifth placement path (cycle 2026-09-04, two
            // assertValueRange): the followers are lessons DISPLACED in
            // collusion with the extraction; their slide destinations need
            // the same per-cell legality the mover's own landing already
            // has, or a follower lands outside its value range and the next
            // solver start (cache-free clone) explodes. The chain translate
            // got this guard in TASK-578; this was the last unguarded path.
            // A PINNED follower rejects the whole plan too (its own slot is
            // its only legal one): a capacity loss by design - the safe
            // direction, the pre-guard code slid pinned lessons instead.
            if (back == null || !f.isSlotLegalFresh(back)) {
                LOG.infof("One-off finisher: extraction of lesson %s rejected:"
                                + " follower %s cannot slide back inside its fresh value range",
                        l.id, f.id);
                return false; // no legal slide plan: no candidate can rescue it
            }
            slides.put(f, back);
        }
        // 2) find the extraction landing with the v1.x guards but WITHOUT tail-departure
        List<Timeslot> candidates = range.stream()
                .filter(ts -> ts != l.timeslot)
                .filter(ts -> targetDate == null || ts.getDate().equals(targetDate))
                .filter(ts -> !idx.teacherBusyExcluding(l, ts))
                .filter(ts -> !idx.groupBusyExcluding(l, ts))
                .filter(ts -> idx.landingAdjacent(l.course.studentGroup, ts))
                .filter(ts -> idx.landingQuotaSafe(l.course.studentGroup, ts))
                .filter(ts -> idx.moveH32Neutral(l, ts))
                .sorted(Comparator
                        .<Timeslot, Boolean>comparing(ts -> !idx.isUnderQuotaDay(l.course.studentGroup, ts))
                        .thenComparing(ts -> ts.startDateTime))
                .collect(Collectors.toList());
        for (Timeslot target : candidates) {
            Room room = idx.resolveRoom(l, target, l.room);
            if (room == null) {
                continue;
            }
            // 3) verify the POST state: every mover (l and the followers) must find
            //    teacher/group free and a room at its destination, against everyone
            //    else INCLUDING the other movers at their new slots.
            java.util.Map<Lesson, Timeslot> dests = new HashMap<>(slides);
            dests.put(l, target);
            boolean postOk = true;
            for (var e2 : dests.entrySet()) {
                Lesson m = e2.getKey();
                Timeslot dst = e2.getValue();
                if (idx.teacherBusyExcludingAll(dests, m, dst)
                        || idx.groupBusyExcludingAll(dests, m, dst)) {
                    postOk = false;
                    break;
                }
                Room r = m == l ? room
                        : idx.resolveRoomPost(dests, m, dst);
                if (r == null) {
                    postOk = false;
                    break;
                }
            }
            if (!postOk) {
                continue;
            }
            // 4) apply: extraction + slides + index update
            for (var e2 : slides.entrySet()) {
                Lesson f = e2.getKey();
                Timeslot dst = e2.getValue();
                Room fr = idx.resolveRoomPost(dests, f, dst);
                Timeslot fOld = f.timeslot;
                Room fOldRoom = f.room;
                moveTimeslot.accept(f, dst);
                moveRoom.accept(f, fr);
                idx.apply(f, fOld, fOldRoom, dst, fr);
            }
            Timeslot oldTs = l.timeslot;
            Room oldRoom = l.room;
            moveTimeslot.accept(l, target);
            moveRoom.accept(l, room);
            idx.apply(l, oldTs, oldRoom, target, room);
            return true;
        }
        return false;
    }

    // ── shared move machinery ───────────────────────────────────────────────────

    /**
     * Moves {@code l} to the first legal slot (teacher free, group free, a compatible
     * room free at the target, the slot inside the lesson's own VRP range); when
     * {@code targetDate} is non-null only that date's slots are considered (family C).
     * Room resolution: keep the current room if free at the target, else a free room
     * satisfying the lesson's tags, else any free assignable room (only when the
     * lesson carries no tag).
     */
    private static boolean tryMove(Lesson l, Index idx,
                                   BiConsumer<Lesson, Timeslot> moveTimeslot,
                                   BiConsumer<Lesson, Room> moveRoom,
                                   LocalDate targetDate) {
        if (l == null || l.course == null || l.course.teacher == null) {
            return false;
        }
        List<Timeslot> range = l.getAvailableTimeslotsFresh();         // (TASK-578: FRESH, stale-cache crash class)
        if (range.isEmpty()) {
            return false;
        }
        // v1 guards (round-8 shell-game cure). DEPARTURE: only the group-day's LAST
        // lesson moves (removing a tail never opens an interior H17 gap and never
        // touches the first hour: H31-safe); an only-lesson day may also move.
        if (!idx.isDayTail(l)) {
            return false;
        }
        // DIAG (mech, laptop): per-stage candidate counts, logged when a move dies here.
        long d0 = range.stream().filter(ts -> ts != l.timeslot).count();
        long d1 = range.stream().filter(ts -> ts != l.timeslot)
                .filter(ts -> targetDate == null || ts.getDate().equals(targetDate)).count();
        long d2 = range.stream().filter(ts -> ts != l.timeslot)
                .filter(ts -> targetDate == null || ts.getDate().equals(targetDate))
                .filter(ts -> !idx.teacherBusy(l.course.teacher, ts)).count();
        List<Timeslot> candidates = range.stream()
                .filter(ts -> ts != l.timeslot)
                .filter(ts -> targetDate == null || ts.getDate().equals(targetDate))
                .filter(ts -> !idx.teacherBusy(l.course.teacher, ts))
                .filter(ts -> !idx.groupBusy(l.course.studentGroup, ts))
                // LANDING contiguity: adjacent to the target day's existing block, or
                // the day is empty (round 8: the G14 fill landed with a 2-slot gap).
                .filter(ts -> idx.landingAdjacent(l.course.studentGroup, ts))
                // LANDING quota: a quota-scoped group may not be pushed past its
                // declared aula+lab for that date.
                .filter(ts -> idx.landingQuotaSafe(l.course.studentGroup, ts))
                // v1.3 COMPENSATED move (round-11 root cause): an at-quota donor gives
                // up one hour (H32 -1) only when the landing fills an under-quota day
                // of the SAME group (H32 +1): net H32 zero, H7 closed. Evaluating the
                // departure in isolation (v1.1/v1.2 donor-strict) forbade every move,
                // because every ordinary day sits exactly at quota.
                .filter(ts -> idx.moveH32Neutral(l, ts))
                // v1.5 landing PREFERENCE (round 13b): under-quota same-group days first -
                // the compensated move exists to fill a deficit, and v1.4 let the
                // at-quota border landings win the sort instead (G13 11-06 stayed at 4h
                // while the relief landed on 15-16/09 borders).
                .sorted(Comparator
                        .<Timeslot, Boolean>comparing(ts -> !idx.isUnderQuotaDay(l.course.studentGroup, ts))
                        .thenComparing(ts -> ts.startDateTime))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            long d3 = range.stream().filter(ts -> ts != l.timeslot)
                    .filter(ts -> targetDate == null || ts.getDate().equals(targetDate))
                    .filter(ts -> !idx.teacherBusy(l.course.teacher, ts))
                    .filter(ts -> !idx.groupBusy(l.course.studentGroup, ts)).count();
            long d4 = range.stream().filter(ts -> ts != l.timeslot)
                    .filter(ts -> targetDate == null || ts.getDate().equals(targetDate))
                    .filter(ts -> !idx.teacherBusy(l.course.teacher, ts))
                    .filter(ts -> !idx.groupBusy(l.course.studentGroup, ts))
                    .filter(ts -> idx.landingAdjacent(l.course.studentGroup, ts)).count();
            long d5 = range.stream().filter(ts -> ts != l.timeslot)
                    .filter(ts -> targetDate == null || ts.getDate().equals(targetDate))
                    .filter(ts -> !idx.teacherBusy(l.course.teacher, ts))
                    .filter(ts -> !idx.groupBusy(l.course.studentGroup, ts))
                    .filter(ts -> idx.landingAdjacent(l.course.studentGroup, ts))
                    .filter(ts -> idx.landingQuotaSafe(l.course.studentGroup, ts)).count();
            LOG.infof("DIAG tryMove dead: lesson=%s group=%s range=%d sameDate=%d teacherFree=%d groupFree=%d adjacent=%d quotaSafe=%d h32neutral=0",
                    l.id, l.course.studentGroup.name, d0, d1, d2, d3, d4, d5);
            range.stream().filter(ts -> ts != l.timeslot)
                    .filter(ts -> targetDate == null || ts.getDate().equals(targetDate))
                    .filter(ts -> !idx.teacherBusy(l.course.teacher, ts))
                    .filter(ts -> !idx.groupBusy(l.course.studentGroup, ts))
                    .filter(ts -> idx.landingAdjacent(l.course.studentGroup, ts))
                    .limit(6)
                    .forEach(ts -> LOG.infof("DIAG cand: %s %s hours=%d declared-row=%s",
                            ts.getDate(), ts.startDateTime.toLocalTime(),
                            idx.byGroupDay.getOrDefault(l.course.studentGroup, Map.of())
                                    .getOrDefault(ts.getDate(), List.of()).size(),
                            String.valueOf(SchoolSchedule.getExpectedHoursRow(l.course.studentGroup.id, ts.getDate()) != null
                                    ? SchoolSchedule.getExpectedHoursRow(l.course.studentGroup.id, ts.getDate()).aulaHours
                                      + SchoolSchedule.getExpectedHoursRow(l.course.studentGroup.id, ts.getDate()).labHours
                                    : "null")));
        }
        Timeslot oldTs = l.timeslot;
        Room oldRoom = l.room;
        for (Timeslot ts : candidates) {
            Room room = idx.resolveRoom(l, ts, oldRoom);
            if (l.id != null && l.id == 10057L) {
                LOG.infof("DIAG 10057 cand %s %s room=%s", ts.getDate(), ts.startDateTime.toLocalTime(),
                        room == null ? "NULL" : room.name);
            }
            if (room == null) {
                continue;
            }
            moveTimeslot.accept(l, ts);
            moveRoom.accept(l, room);
            // DIAG (mech, laptop, round 14): every applied move logged with the full
            // day-slot context of the landing date, to trace gap creation.
            if ((l.course.studentGroup != null && l.course.studentGroup.name.equals("G14"))
                    || "2027-04-30".equals(ts.getDate().toString())) {
                StringBuilder ctx = new StringBuilder();
                idx.byGroupDay.getOrDefault(l.course.studentGroup, Map.of())
                        .getOrDefault(ts.getDate(), List.of()).stream()
                        .sorted(Comparator.comparing(x -> x.timeslot.startDateTime))
                        .limit(8).forEach(x -> ctx.append(x.timeslot.startDateTime.toLocalTime()).append(" "));
                LOG.infof("DIAG move applied: lesson=%s subj=%s group=%s donor=%s@%s -> landing=%s@%s room=%s | group slots on landing date now: %s",
                        l.id, l.course.subject, l.course.studentGroup.name,
                        oldTs.getDate(), oldTs.startDateTime.toLocalTime(),
                        ts.getDate(), ts.startDateTime.toLocalTime(),
                        room == null ? "null" : room.name, ctx);
            }
            idx.apply(l, oldTs, oldRoom, ts, room);
            return true;
        }
        return false;
    }

    /** Live per-slot occupancy over the schedule's current placement. */
    private static final class Index {
        private final SchoolSchedule schedule;
        private final Map<com.school.planner.domain.Teacher, Map<LocalDate, List<Lesson>>> byTeacherDay = new HashMap<>();
        private final Map<com.school.planner.domain.StudentGroup, Map<LocalDate, List<Lesson>>> byGroupDay = new HashMap<>();
        private final Map<Timeslot, Set<com.school.planner.domain.Teacher>> teacherAtSlot = new HashMap<>();
        private final Map<Timeslot, Set<com.school.planner.domain.StudentGroup>> groupAtSlot = new HashMap<>();
        private final Map<Timeslot, Map<Room, Integer>> roomAtSlot = new HashMap<>();
        private final Set<String> skippedOverloads = new HashSet<>();
        private final Set<String> skippedFloors = new HashSet<>();
        int rejected = 0;

        Index(SchoolSchedule schedule) {
            this.schedule = schedule;
            for (Lesson l : schedule.getLessons()) {
                if (l.timeslot == null || l.course == null) {
                    continue;
                }
                if (l.course.teacher != null) {
                    byTeacherDay.computeIfAbsent(l.course.teacher, t -> new HashMap<>())
                            .computeIfAbsent(l.timeslot.getDate(), d -> new ArrayList<>()).add(l);
                    teacherAtSlot.computeIfAbsent(l.timeslot, t -> new HashSet<>()).add(l.course.teacher);
                }
                if (l.course.studentGroup != null) {
                    byGroupDay.computeIfAbsent(l.course.studentGroup, g -> new HashMap<>())
                            .computeIfAbsent(l.timeslot.getDate(), d -> new ArrayList<>()).add(l);
                    groupAtSlot.computeIfAbsent(l.timeslot, t -> new HashSet<>()).add(l.course.studentGroup);
                }
                if (l.room != null) {
                    roomAtSlot.computeIfAbsent(l.timeslot, t -> new HashMap<>())
                            .merge(l.room, 1, Integer::sum);
                }
            }
        }

        void apply(Lesson l, Timeslot oldTs, Room oldRoom, Timeslot ts, Room room) {
            // remove from the OLD slot (the callbacks have already moved l's fields,
            // so every read below uses oldTs/oldRoom - never l.timeslot/l.room)
            if (l.course.teacher != null && oldTs != null) {
                var m = byTeacherDay.getOrDefault(l.course.teacher, Map.of())
                        .get(oldTs.getDate());
                if (m != null) {
                    m.remove(l);
                }
                var s = teacherAtSlot.get(oldTs);
                if (s != null) {
                    s.remove(l.course.teacher);
                }
            }
            if (l.course.studentGroup != null && oldTs != null) {
                var m = byGroupDay.getOrDefault(l.course.studentGroup, Map.of())
                        .get(oldTs.getDate());
                if (m != null) {
                    m.remove(l);
                }
                var s = groupAtSlot.get(oldTs);
                if (s != null) {
                    s.remove(l.course.studentGroup);
                }
            }
            if (oldRoom != null && oldTs != null) {
                roomAtSlot.getOrDefault(oldTs, Map.of()).remove(oldRoom);
            }
            // place at the NEW slot
            if (l.course.teacher != null) {
                byTeacherDay.computeIfAbsent(l.course.teacher, t -> new HashMap<>())
                        .computeIfAbsent(ts.getDate(), d -> new ArrayList<>()).add(l);
                teacherAtSlot.computeIfAbsent(ts, t -> new HashSet<>()).add(l.course.teacher);
            }
            if (l.course.studentGroup != null) {
                byGroupDay.computeIfAbsent(l.course.studentGroup, g -> new HashMap<>())
                        .computeIfAbsent(ts.getDate(), d -> new ArrayList<>()).add(l);
                groupAtSlot.computeIfAbsent(ts, t -> new HashSet<>()).add(l.course.studentGroup);
            }
            roomAtSlot.computeIfAbsent(ts, t -> new HashMap<>()).merge(room, 1, Integer::sum);
        }


        /** True when l is its group's LAST lesson of that day (or the day's only one). */
        boolean isDayTail(Lesson l) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) {
                return false;
            }
            List<Lesson> day = byGroupDay.getOrDefault(l.course.studentGroup, Map.of())
                    .getOrDefault(l.timeslot.getDate(), List.of());
            if (day.size() <= 1) {
                return true;
            }
            return day.stream().map(x -> x.timeslot)
                    .max(Comparator.comparing(ts -> ts.startDateTime))
                    .map(max -> max.equals(l.timeslot)).orElse(false);
        }

        /**
         * v1.3 COMPENSATED departure (round-11 root cause): the donor's possible H32
         * deficit is evaluated AGAINST the landing day of the same group, never in
         * isolation. Cases: donor over quota -> free; donor at/under quota -> the
         * landing date must be under quota by >= 1 so the move is H32-neutral. The
         * lesson's group is the same on both sides by construction (we move the lesson
         * inside its own VRP range), so this is a true same-group transfer.
         */
        boolean moveH32Neutral(Lesson l, Timeslot target) {
            if (l.course == null || l.course.studentGroup == null || l.timeslot == null) {
                return true;
            }
            var group = l.course.studentGroup;
            if (SchoolScheduleConstraintProvider.isQuotaFillExcluded(group.name)) {
                return true; // pool-cap groups: the floor family owns their shape
            }
            var donorRow = SchoolSchedule.getExpectedHoursRow(group.id, l.timeslot.getDate());
            if (donorRow == null) {
                return true;
            }
            int donorDeclared = donorRow.aulaHours + donorRow.labHours;
            if (donorDeclared <= 0) {
                return true;
            }
            int donorHours = byGroupDay.getOrDefault(group, Map.of())
                    .getOrDefault(l.timeslot.getDate(), List.of()).size();
            if (donorHours - 1 >= donorDeclared) {
                return true; // donor over quota: the hour was surplus, free to move
            }
            // donor would go under quota: the landing must pay it back
            var landingRow = SchoolSchedule.getExpectedHoursRow(group.id, target.getDate());
            if (landingRow == null) {
                return false;
            }
            int landingDeclared = landingRow.aulaHours + landingRow.labHours;
            int landingHours = byGroupDay.getOrDefault(group, Map.of())
                    .getOrDefault(target.getDate(), List.of()).size();
            return landingHours + 1 <= landingDeclared; // fills a real deficit
        }

        /**
         * v1.4 landing = the RATIFIED QuotaFillRepair-v2 border-aware rule (round-12
         * option 2): allowed slots are the block's edges (before-first / after-last)
         * AND the interior of an EXISTING gap (filling it closes an H17 hole: strictly
         * better than the v1.3 strict adjacency, which reimplemented a sticter version
         * of a house rule and emptied the funnel by construction on this checkpoint).
         */
        boolean landingAdjacent(com.school.planner.domain.StudentGroup g, Timeslot ts) {
            if (g == null) {
                return true;
            }
            List<Lesson> day = byGroupDay.getOrDefault(g, Map.of()).getOrDefault(ts.getDate(), List.of());
            if (day.isEmpty()) {
                return true;
            }
            Timeslot first = day.stream().map(x -> x.timeslot)
                    .min(Comparator.comparing(t -> t.startDateTime)).orElse(null);
            Timeslot last = day.stream().map(x -> x.timeslot)
                    .max(Comparator.comparing(t -> t.startDateTime)).orElse(null);
            if (first == null || last == null) {
                return true;
            }
            // adjacency by wall-clock delta on the same date: one slot step before the
            // first or after the last. The school grid is uniform (steps of 1h with a
            // lunch jump), so compare by start-of-day minute offsets via startDateTime.
            java.time.Duration before = java.time.Duration.between(ts.startDateTime, first.startDateTime);
            java.time.Duration after = java.time.Duration.between(last.startDateTime, ts.startDateTime);
            if (!before.isNegative() && before.toMinutes() > 0) {
                return isNextGridStep(g, ts, first, true);
            }
            if (!after.isNegative() && after.toMinutes() > 0) {
                return isNextGridStep(g, last, ts, false);
            }
            // interior of an existing gap: the slot sits strictly between two lessons
            // of the day, so landing here FILLS a hole (H17 improves). QFR-v2 semantics.
            return ts.startDateTime.isAfter(first.startDateTime)
                    && ts.startDateTime.isBefore(last.startDateTime)
                    && dayLessonsAt(g, ts.getDate()).stream().noneMatch(x -> x.timeslot.equals(ts));
        }

        /** True when the group's placed hours on that date sit below the declared quota. */
        boolean isUnderQuotaDay(com.school.planner.domain.StudentGroup g, Timeslot ts) {
            if (g == null) {
                return false;
            }
            if (SchoolScheduleConstraintProvider.isQuotaFillExcluded(g.name)) {
                return false;
            }
            var row = SchoolSchedule.getExpectedHoursRow(g.id, ts.getDate());
            if (row == null) {
                return false;
            }
            int declared = row.aulaHours + row.labHours;
            if (declared <= 0) {
                return false;
            }
            int hours = byGroupDay.getOrDefault(g, Map.of()).getOrDefault(ts.getDate(), List.of()).size();
            return hours > 0 && hours < declared;
        }

        private List<Lesson> dayLessonsAt(com.school.planner.domain.StudentGroup g, LocalDate d) {
            return byGroupDay.getOrDefault(g, Map.of()).getOrDefault(d, List.of());
        }

        /** The slot step the school grid actually uses between two adjacent lessons. */
        private boolean isNextGridStep(com.school.planner.domain.StudentGroup g, Timeslot a, Timeslot b,
                                       boolean aBeforeB) {
            // A same-day pair is grid-adjacent when no OTHER timeslot of that date sits
            // strictly between them AND the pair share the date (the grid's own slot
            // ordering carries the lunch jump; between-ness is the honest test).
            if (!a.getDate().equals(b.getDate())) {
                return false;
            }
            // round-14 fix: the aBeforeB=false branch had the operands SWAPPED
            // (after-b && before-a with a<b is unsatisfiable, so noneMatch always
            // passed and a landing with free grid slots in between slipped through:
            // the exact G14 30-04 gap zai's DIAG caught).
            return schedule.getTimeslots().stream()
                    .filter(t -> t.getDate().equals(a.getDate()))
                    .noneMatch(t -> (aBeforeB
                            ? t.startDateTime.isAfter(a.startDateTime) && t.startDateTime.isBefore(b.startDateTime)
                            : t.startDateTime.isAfter(a.startDateTime) && t.startDateTime.isBefore(b.startDateTime)));
        }

        /**
         * Quota-scoped group has headroom (placed < declared aula+lab) on that date.
         * Round-9 lesson: a DECLARED-EMPTY in-session day (an H34 cell, the golden-
         * practice debt we deliberately keep empty) is UNTOUCHABLE - landing a single
         * hour there passes the naive headroom test yet violates H32 AND H33 at once
         * (the -16 regression). All-or-nothing means nothing, for a one-hour mover.
         */
        boolean landingQuotaSafe(com.school.planner.domain.StudentGroup g, Timeslot ts) {
            if (g == null) {
                return true;
            }
            var row = SchoolSchedule.getExpectedHoursRow(g.id, ts.getDate());
            if (row == null) {
                return true; // no-session days are the VRP's call, not ours
            }
            int declared = row.aulaHours + row.labHours;
            if (declared <= 0) {
                return true;
            }
            int hours = byGroupDay.getOrDefault(g, Map.of()).getOrDefault(ts.getDate(), List.of()).size();
            if (hours == 0) {
                return false; // declared in-session day left empty: an accepted H34 cell
            }
            // Over-quota landing (hours+1 > declared) pays M33 MEDIUM, not hard: the
            // lexicographic oracle arbitrates hard-vs-medium, and closing an H7/H29/H33
            // hard unit dominates any M33 surplus (round-11 semantics: v1.1 treated the
            // surplus as if it were hard and left the finisher with zero legal landings).
            return true;
        }


        // ── v2 extractor support ────────────────────────────────────────────────

        boolean isDayFirst(Lesson l) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) {
                return false;
            }
            List<Lesson> day = dayLessonsAt(l.course.studentGroup, l.timeslot.getDate());
            return day.stream().map(x -> x.timeslot)
                    .min(Comparator.comparing(t -> t.startDateTime))
                    .map(min -> min.equals(l.timeslot)).orElse(false);
        }

        /** Same-group lessons of l's day strictly after l's slot (the slide set). */
        List<Lesson> followersOf(Lesson l) {
            if (l.timeslot == null || l.course == null || l.course.studentGroup == null) {
                return List.of();
            }
            return dayLessonsAt(l.course.studentGroup, l.timeslot.getDate()).stream()
                    .filter(x -> x.timeslot.startDateTime.isAfter(l.timeslot.startDateTime))
                    .sorted(Comparator.comparing(x -> x.timeslot.startDateTime))
                    .collect(Collectors.toList());
        }

        /** The greatest existing grid timeslot of the date strictly before ts. */
        Timeslot previousGridSlot(Timeslot ts) {
            Timeslot best = null;
            for (Timeslot t : schedule.getTimeslots()) {
                if (t.getDate().equals(ts.getDate()) && t.startDateTime.isBefore(ts.startDateTime)
                        && (best == null || t.startDateTime.isAfter(best.startDateTime))) {
                    best = t;
                }
            }
            return best;
        }

        boolean teacherBusyExcluding(Lesson self, Timeslot ts) {
            return schedule.getLessons().stream().anyMatch(x -> x != self && x.timeslot == ts
                    && x.course != null && x.course.teacher != null
                    && x.course.teacher.equals(self.course.teacher));
        }

        boolean groupBusyExcluding(Lesson self, Timeslot ts) {
            if (self.course.studentGroup == null) {
                return false;
            }
            return schedule.getLessons().stream().anyMatch(x -> x != self && x.timeslot == ts
                    && x.course != null && x.course.studentGroup != null
                    && x.course.studentGroup.equals(self.course.studentGroup));
        }

        /** POST-state checks: the movers' destinations are excluded from "busy". */
        boolean teacherBusyExcludingAll(java.util.Map<Lesson, Timeslot> movers, Lesson self, Timeslot dst) {
            return schedule.getLessons().stream().anyMatch(x -> {
                if (x == self) {
                    return false;
                }
                Timeslot at = movers.get(x) != null ? movers.get(x) : x.timeslot;
                return at == dst && x.course != null && x.course.teacher != null
                        && x.course.teacher.equals(self.course.teacher);
            });
        }

        boolean groupBusyExcludingAll(java.util.Map<Lesson, Timeslot> movers, Lesson self, Timeslot dst) {
            if (self.course.studentGroup == null) {
                return false;
            }
            return schedule.getLessons().stream().anyMatch(x -> {
                if (x == self) {
                    return false;
                }
                Timeslot at = movers.get(x) != null ? movers.get(x) : x.timeslot;
                return at == dst && x.course != null && x.course.studentGroup != null
                        && x.course.studentGroup.equals(self.course.studentGroup);
            });
        }

        /** Room for m at dst in the POST state (movers' rooms vacate their old slots). */
        Room resolveRoomPost(java.util.Map<Lesson, Timeslot> movers, Lesson m, Timeslot dst) {
            Map<Room, Integer> used = new HashMap<>();
            for (Lesson x : schedule.getLessons()) {
                if (x == m || x.room == null) {
                    continue;
                }
                Timeslot at = movers.get(x) != null ? movers.get(x) : x.timeslot;
                if (at == dst) {
                    used.merge(x.room, 1, Integer::sum);
                }
            }
            if (m.room != null && !used.containsKey(m.room)) {
                return m.room;
            }
            var tags = m.course.requiredRoomTags;
            if (tags != null && !tags.isEmpty()) {
                for (Room r : SchoolSchedule.getAllRooms()) {
                    if (r.isAssignable() && r.tags != null && r.tags.containsAll(tags)
                            && !used.containsKey(r)) {
                        return r;
                    }
                }
                return null;
            }
            for (Room r : SchoolSchedule.getAllRooms()) {
                if (r.isAssignable() && !used.containsKey(r)) {
                    return r;
                }
            }
            return null;
        }


        /** The contiguous same-group run (same date, adjacent grid slots) containing x. */
        List<Lesson> contiguousRun(Lesson x) {
            List<Lesson> day = dayLessonsAt(x.course.studentGroup, x.timeslot.getDate());
            List<Lesson> sorted = day.stream()
                    .sorted(Comparator.comparing(z -> z.timeslot.startDateTime))
                    .collect(Collectors.toList());
            int i = sorted.indexOf(x);
            if (i < 0) {
                return List.of(x);
            }
            int lo = i;
            while (lo > 0 && idxAdjacent(sorted.get(lo - 1).timeslot, sorted.get(lo).timeslot)) {
                lo--;
            }
            int hi = i;
            while (hi < sorted.size() - 1
                    && idxAdjacent(sorted.get(hi).timeslot, sorted.get(hi + 1).timeslot)) {
                hi++;
            }
            return new ArrayList<>(sorted.subList(lo, hi + 1));
        }

        private boolean idxAdjacent(Timeslot a, Timeslot b) {
            // adjacent = no other grid slot of that date strictly between them
            return gridSlotsOf(a.getDate()).stream()
                    .filter(t -> t.startDateTime.isAfter(a.startDateTime)
                            && t.startDateTime.isBefore(b.startDateTime))
                    .count() == 0
                    || gridSlotsOf(a.getDate()).stream()
                            .filter(t -> t.startDateTime.isAfter(b.startDateTime)
                                    && t.startDateTime.isBefore(a.startDateTime))
                            .count() == 0;
        }

        List<Timeslot> gridSlotsOf(java.time.LocalDate d) {
            return schedule.getTimeslots().stream()
                    .filter(t -> t.getDate().equals(d))
                    .sorted(Comparator.comparing(t -> t.startDateTime))
                    .collect(Collectors.toList());
        }

        boolean teacherBusyExcludingSet(List<Lesson> set, Lesson self, Timeslot ts) {
            return schedule.getLessons().stream().anyMatch(x -> x != self && !set.contains(x)
                    && x.timeslot != null && x.timeslot.equals(ts)
                    && x.course != null && x.course.teacher != null
                    && x.course.teacher.equals(self.course.teacher));
        }

        boolean groupBusyExcludingSet(List<Lesson> set, Lesson self, Timeslot ts) {
            if (self.course.studentGroup == null) {
                return false;
            }
            return schedule.getLessons().stream().anyMatch(x -> x != self && !set.contains(x)
                    && x.timeslot != null && x.timeslot.equals(ts)
                    && x.course != null && x.course.studentGroup != null
                    && x.course.studentGroup.equals(self.course.studentGroup));
        }

        boolean teacherBusy(com.school.planner.domain.Teacher t, Timeslot ts) {
            return teacherAtSlot.getOrDefault(ts, Set.of()).contains(t);
        }

        boolean groupBusy(com.school.planner.domain.StudentGroup g, Timeslot ts) {
            return g != null && groupAtSlot.getOrDefault(ts, Set.of()).contains(g);
        }

        List<Lesson> lessonsOf(com.school.planner.domain.Teacher t, LocalDate d) {
            return byTeacherDay.getOrDefault(t, Map.of()).getOrDefault(d, List.of());
        }

        /** 0 for a lesson at the edge of its group's day (preferred mover), higher inside. */
        int edgeRank(Lesson l) {
            if (l == null || l.timeslot == null || l.course == null || l.course.studentGroup == null) {
                return 9;
            }
            List<Lesson> day = byGroupDay.getOrDefault(l.course.studentGroup, Map.of())
                    .getOrDefault(l.timeslot.getDate(), List.of());
            if (day.size() <= 1) {
                return 0;
            }
            Timeslot min = day.stream().map(x -> x.timeslot).min(Comparator.comparing(ts -> ts.startDateTime)).orElse(null);
            Timeslot max = day.stream().map(x -> x.timeslot).max(Comparator.comparing(ts -> ts.startDateTime)).orElse(null);
            if (min == null || l.timeslot.equals(min) || l.timeslot.equals(max)) {
                return 0;
            }
            return 5;
        }

        Map<com.school.planner.domain.Teacher, Map<LocalDate, Integer>> teacherDayOverloads() {
            Map<com.school.planner.domain.Teacher, Map<LocalDate, Integer>> out = new HashMap<>();
            for (var te : byTeacherDay.entrySet()) {
                var teacher = te.getKey();
                if (teacher.pseudo) {
                    continue;
                }
                for (var de : te.getValue().entrySet()) {
                    int hours = de.getValue().size();
                    int cap = teacher.getEffectiveMaxDailyHours();
                    if (hours > cap && !skippedOverloads.contains(teacher.name + "|" + de.getKey())) {
                        out.computeIfAbsent(teacher, t -> new HashMap<>()).put(de.getKey(), hours - cap);
                    }
                }
            }
            return out;
        }

        void skipOverload(com.school.planner.domain.Teacher t, LocalDate d) {
            skippedOverloads.add(t.name + "|" + d);
        }

        Map<com.school.planner.domain.StudentGroup, List<LocalDate>> underFloorDays() {
            Map<com.school.planner.domain.StudentGroup, List<LocalDate>> out = new HashMap<>();
            for (var ge : byGroupDay.entrySet()) {
                var group = ge.getKey();
                Integer floor = SchoolSchedule.getDayFloor(group.id);
                if (floor == null || floor <= 0) {
                    continue;
                }
                for (var de : ge.getValue().entrySet()) {
                    int hours = de.getValue().size();
                    if (hours < floor && !skippedFloors.contains(group.name + "|" + de.getKey())) {
                        out.computeIfAbsent(group, g -> new ArrayList<>()).add(de.getKey());
                    }
                }
            }
            return out;
        }

        int floorDeficit(com.school.planner.domain.StudentGroup g, LocalDate d) {
            Integer floor = SchoolSchedule.getDayFloor(g.id);
            int hours = byGroupDay.getOrDefault(g, Map.of()).getOrDefault(d, List.of()).size();
            return floor == null ? 0 : Math.max(0, floor - hours);
        }

        void skipUnderFloor(com.school.planner.domain.StudentGroup g, LocalDate d) {
            skippedFloors.add(g.name + "|" + d);
        }

        /** Donor: unpinned lesson of the group on a SURPLUS day (hours > floor), edge-ranked. */
        Lesson bestDonor(com.school.planner.domain.StudentGroup g, LocalDate targetDate) {
            Integer floor = SchoolSchedule.getDayFloor(g.id);
            Lesson best = null;
            int bestRank = 99;
            for (var de : byGroupDay.getOrDefault(g, Map.of()).entrySet()) {
                if (de.getKey().equals(targetDate)) {
                    continue;
                }
                int hours = de.getValue().size();
                if (floor != null && hours <= floor) {
                    continue; // no surplus over the floor
                }
                for (Lesson l : de.getValue()) {
                    if (l.pinned) {
                        continue;
                    }
                    int rank = edgeRank(l);
                    // H31: the donor day's first hour never moves
                    if (rank > 0 && isDayFirst(g, de.getKey(), l)) {
                        continue;
                    }
                    if (rank < bestRank) {
                        best = l;
                        bestRank = rank;
                    }
                }
            }
            return best;
        }

        private boolean isDayFirst(com.school.planner.domain.StudentGroup g, LocalDate d, Lesson l) {
            List<Lesson> day = byGroupDay.getOrDefault(g, Map.of()).getOrDefault(d, List.of());
            return day.stream().map(x -> x.timeslot)
                    .min(Comparator.comparing(ts -> ts.startDateTime))
                    .map(min -> min.equals(l.timeslot)).orElse(false);
        }

        /** Same room if free at ts, else a tag-compatible free one, else any free assignable. */
        Room resolveRoom(Lesson l, Timeslot ts, Room currentRoom) {
            OneOffFinisher.lastDst.set(ts.getDate() + " " + ts.startDateTime.toLocalTime());
            Map<Room, Integer> used = roomAtSlot.getOrDefault(ts, Map.of());
            if (currentRoom != null && !used.containsKey(currentRoom)) {
                return currentRoom;
            }
            var tags = l.course.requiredRoomTags;
            if (tags != null && !tags.isEmpty()) {
                int tagMatch = 0;
                for (Room r : SchoolSchedule.getAllRooms()) {
                    if (r.isAssignable() && r.tags != null && r.tags.containsAll(tags)) {
                        tagMatch++;
                        if (!used.containsKey(r)) {
                            return r;
                        }
                    }
                }
                LOG.infof("DIAG resolveRoom tagged-miss: lesson=%s dst=%s tags=%s allRooms=%d tagMatch=%d usedAtDst=%s",
                        l.id, String.valueOf(lastDst.get()), tags, SchoolSchedule.getAllRooms().size(), tagMatch,
                        used.keySet().stream().map(x -> x.name).collect(Collectors.joining(",")));
                return null; // a tagged lesson never lands in an untagged room
            }
            for (Room r : SchoolSchedule.getAllRooms()) {
                if (r.isAssignable() && !used.containsKey(r)) {
                    return r;
                }
            }
            return null;
        }
    }
}
