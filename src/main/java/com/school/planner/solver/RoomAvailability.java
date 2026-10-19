package com.school.planner.solver;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.Timeslot;

/**
 * TASK-652: the ONE per-pair room-availability predicate. Eight-plus call
 * sites hand-rolled "is this room free at this timeslot?" with drifting
 * equality semantics; the semantics (the domain's own equals, null guards,
 * mover self-exclusion) live here now. The ITERATION stays at each site: the
 * occupancy INDEX - not the predicate - is the load-bearing performance part
 * of each pass, and the indexes are deliberately heterogeneous.
 *
 * <p><b>Site census</b> (2026-09-27, ~11 sites). DELEGATING: QuotaFillRepair
 * findMove (inline scan), SurplusHarvest.Occupancy.roomBusy (bySlot lists),
 * ViolationScopedSteepestFinisher.generateRoomVariants (bulk set build),
 * OneOffFinisher block-move roomTaken (inline scan). KEPT BY DESIGN, with the
 * reason: HRF/CSF roomTaken (string-keyed roomCell, incrementally maintained;
 * the hour-key carries the F5 single-schema invariant),
 * RoomTailFinisher/PureHoleRepair.Grid cellOk (single-holder roomCell probe
 * with ignore-exclusions), OneOffFinisher.resolveRoom (incremental per-slot
 * Room-to-count), EmptyDayFinisher roomsByTs (incremental Map&lt;Long,Set&lt;Long&gt;
 * with undo), ViolationScopedSteepestFinisher.holdersBySlotRoom (holder COUNT
 * map: a Set would mask a third co-present lesson, review F2). When an
 * exemption is ever wired into a kept site, route the per-pair check through
 * {@link #conflicts} so the exemption semantics stay single-sourced.</p>
 *
 * <p><b>The H2 co-presence exemption is a parameter</b> because no current
 * copy models it: repair passes treat golden-practiced guest pairs as
 * conflicts (stricter than the constraint itself). Sites that iterate
 * lessons can pass an exemption to match H2 exactly; {@code null} keeps
 * today's conservative behavior, which is what every migrated site passes.</p>
 */
public final class RoomAvailability {

    private RoomAvailability() {
    }

    /**
     * Functional interface for the H2 co-presence exemption: can these two
     * lessons legally share a room? The CANONICAL pair predicate is
     * {@code BaseConstraintProvider.isCoPresenceGuestPair(a, b)} (same
     * package): delegate to it rather than re-deriving "single tag + golden
     * depth" here. Argument contract: read the CELL from the OCCUPANT
     * argument ({@code b} as {@link #conflicts} passes it; {@code a} may be
     * an unplaced mover with a null timeslot). Pass {@code null} for the
     * conservative behavior (no pair ever shares).
     */
    @FunctionalInterface
    public interface CoPresenceExemption {
        boolean isExempt(Lesson a, Lesson b);
    }

    /**
     * The primitive: does {@code x} occupy {@code room} at {@code ts}?
     * Equality is the DOMAIN's own ({@link Room#equals}/{@link Timeslot#equals}:
     * identity fast path, then id): hand-rolled id-only comparison broke the
     * null-id same-instance fixtures some tests and passes legitimately use.
     * The timeslot is tested FIRST: it is the selective discriminator in
     * ungrouped scans (one slot out of ~1,392) and a guaranteed identity hit
     * in slot-grouped lists. Null-safe on every argument.
     */
    public static boolean occupies(Lesson x, Room room, Timeslot ts) {
        return x != null && room != null && ts != null
                && x.timeslot != null && x.timeslot.equals(ts)
                && x.room != null && x.room.equals(room);
    }

    /**
     * Does {@code other} block {@code mover} from {@code room} at {@code ts}?
     * The mover never blocks itself ({@link Lesson#equals}: identity fast
     * path, then id); an H2-exempt pair does not block either.
     */
    public static boolean conflicts(Lesson mover, Room room, Timeslot ts,
            Lesson other, CoPresenceExemption exemption) {
        if (!occupies(other, room, ts)) {
            return false;
        }
        if (other.equals(mover)) {
            return false; // the mover never blocks itself
        }
        return exemption == null || !exemption.isExempt(mover, other);
    }

    /**
     * Bulk occupancy set keyed by timeslot id (the VSF pattern): one pass
     * over the lessons, membership probes afterwards. Built per lazy pass
     * and dies with it - applied moves change occupancy, so never cache it
     * across a move application.
     */
    public static Map<Long, Set<Long>> occupiedRoomIdsBySlot(Collection<Lesson> lessons) {
        Map<Long, Set<Long>> out = new HashMap<>();
        for (Lesson l : lessons) {
            if (l.timeslot != null && l.timeslot.id != null
                    && l.room != null && l.room.id != null) {
                out.computeIfAbsent(l.timeslot.id, k -> new HashSet<>()).add(l.room.id);
            }
        }
        return out;
    }

    /**
     * Whether any lesson occupies {@code room} at {@code ts} (no mover
     * concept): the SurplusHarvest shape, where the caller checks a room
     * belonging to a different lesson (blocker, donor).
     */
    public static boolean occupied(Room room, Timeslot ts, List<Lesson> atSlot) {
        if (room == null) {
            return false;
        }
        for (Lesson l : atSlot) {
            if (occupies(l, room, ts)) {
                return true;
            }
        }
        return false;
    }
}
