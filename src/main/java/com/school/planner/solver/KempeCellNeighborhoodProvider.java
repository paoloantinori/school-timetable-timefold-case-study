package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedCollection;
import java.util.TreeMap;

import ai.timefold.solver.core.config.solver.PreviewFeature;
import ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel;
import ai.timefold.solver.core.api.domain.common.Lookup;
import ai.timefold.solver.core.preview.api.move.Move;
import ai.timefold.solver.core.preview.api.move.MutableSolutionView;
import ai.timefold.solver.core.preview.api.move.SolutionView;
import ai.timefold.solver.core.preview.api.neighborhood.MoveProvider;
import ai.timefold.solver.core.preview.api.neighborhood.Neighborhood;
import ai.timefold.solver.core.preview.api.neighborhood.NeighborhoodBuilder;
import ai.timefold.solver.core.preview.api.neighborhood.NeighborhoodProvider;
import ai.timefold.solver.core.preview.api.neighborhood.stream.MoveStream;
import ai.timefold.solver.core.preview.api.neighborhood.stream.MoveStreamFactory;
import ai.timefold.solver.core.preview.api.neighborhood.stream.enumerating.BiEnumeratingStream;
import ai.timefold.solver.core.preview.api.neighborhood.stream.enumerating.UniEnumeratingStream;
import ai.timefold.solver.core.preview.api.neighborhood.stream.enumerating.collector.NeighborhoodsCollectors;
import ai.timefold.solver.core.preview.api.neighborhood.stream.function.BiNeighborhoodsMapper;
import ai.timefold.solver.core.preview.api.neighborhood.stream.function.UniNeighborhoodsMapper;
import ai.timefold.solver.core.preview.api.neighborhood.stream.joiner.NeighborhoodsJoiners;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;

/**
 * TASK-560 (banco Kempe, 26/08, decisione (b) della valutazione riusa-prima): the
 * Move Streams (preview, {@link PreviewFeature#NEIGHBORHOODS}) neighborhood that
 * generates WHOLE-CELL transactions for H34's residual empty declared days.
 *
 * <p><b>Why whole-cell and not Kempe-2.</b> A single-hour composite into an empty
 * declared day sits in an H32 valley: the move trades -1 H34 for +(quota-1) H32,
 * so ANY score-monotone acceptor (the bench window is hill climbing, acl=1, no
 * acceptor) refuses the first step. The move must carry the cell to its declared
 * quota in one transaction, together with the donor slides that keep the donor
 * days contiguous (H17) and above their floor (H32/H33). This is the LS-side
 * mirror of QuotaFillRepair v2's gate (c) atomicity - the same law discovered
 * from the deterministic side on 26/08 morning.
 *
 * <p><b>Stream shape</b> (all conditions are indexed joins, no full scans):
 * <ol>
 * <li>target cells: group x timeslot pairs filtered to "first in-window grid slot
 *     of an in-scope declared date" (statics only; our grid carries all 8 slots
 *     per date, so the head is exactly windowMin), then {@code ifNotExists}
 *     lessons keyed by (group, date) = the cell is EMPTY;</li>
 * <li>each cell materialized with (a) ALL the group's placed real-teacher lessons
 *     (join on group, groupBy to list) and (b) EVERY date's timeslots (groupBy on
 *     date key, then one join per cell collecting all the bags), because the
 *     preview SolutionView cannot enumerate facts and the donor slides need
 *     slots of DONOR dates, not just the cell's;</li>
 * <li>the mapper plans the transaction EDF-style: donors yield tail hours with
 *     slide-down of the hours above (contiguity), the cell fills contiguously
 *     from its first slot, donors keep {@code max(own quota, practiced floor)}
 *     hours (gate e); an unreachable quota yields a no-op move (the H32 valley
 *     law: a partial fill is never even offered).</li>
 * </ol>
 *
 * <p>H1/room legality is NOT in the plan: the solver PRICES the executed move
 * (full incremental score) and the hill-climbing acceptor never takes a
 * hard-worsening transaction, which is the bench's zero-hard-added guarantee.
 *
 * <p>BENCH ONLY (TASK-560): referenced exclusively by the discriminator test;
 * no production solver config points here. Any production use is maintainer's call
 * after the bench verdict.
 */
public final class KempeCellNeighborhoodProvider implements NeighborhoodProvider<SchoolSchedule> {

    @Override
    public Neighborhood defineNeighborhood(NeighborhoodBuilder<SchoolSchedule> builder) {
        PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot> timeslotVar = resolveTimeslotVariable(builder);
        builder.add(factory -> kempeStream(factory, timeslotVar));
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot> resolveTimeslotVariable(
            NeighborhoodBuilder<SchoolSchedule> builder) {
        for (var variable : builder.getSolutionMetaModel().entity(Lesson.class).variables()) {
            if (variable instanceof PlanningVariableMetaModel && "timeslot".equals(variable.name())) {
                return (PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot>) variable;
            }
        }
        throw new IllegalStateException("Lesson has no basic 'timeslot' planning variable");
    }

    // ─── stream ───────────────────────────────────────────────────────────────

    /** One empty declared target cell (statics only). */
    private record TargetCell(StudentGroup group, LocalDate date, int quota, Timeslot firstSlot) {
        String dayKey() {
            return group.id + "@" + date;
        }

        Long groupKey() {
            return group.id;
        }

        String dateKey() {
            return date.toString();
        }
    }

    private record CellLesson(TargetCell cell, Lesson lesson) {
    }

    /** One cell with its group's lessons materialized (pre slot-bag join). */
    private record CellLessonsBag(TargetCell cell, List<Lesson> groupLessons) {
        String dateKey() {
            return cell.dateKey();
        }
    }

    private record SlotBag(String dateKey, List<Timeslot> slots) {
    }

    static MoveStream<SchoolSchedule> kempeStream(MoveStreamFactory<SchoolSchedule> factory,
                                                  PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot> timeslotVar) {
        var lessons = factory.forEach(Lesson.class, false) // preview auto-filters pinned entities
                .filter((view, l) -> l.course != null && l.course.studentGroup != null
                        && l.timeslot != null && !l.isPinned());
        var groups = factory.forEach(StudentGroup.class, false)
                .filter((view, g) -> g != null && g.id != null
                        && !SchoolScheduleConstraintProvider.isQuotaFillExcluded(g.name));
        var slots = factory.forEach(Timeslot.class, false)
                .filter((view, t) -> t.getDate() != null);

        // (1) empty declared cells: first in-window grid slot + ifNotExists lesson on (group, date)
        UniEnumeratingStream<SchoolSchedule, TargetCell> cells = groups.join(slots)
                .filter((view, g, t) -> isDeclaredFirstOpenSlot(g, t))
                .map((BiNeighborhoodsMapper<SchoolSchedule, StudentGroup, Timeslot, TargetCell>)
                        (view, g, t) -> new TargetCell(g, t.getDate(), declaredQuota(g.id, t.getDate()), t))
                .ifNotExists(lessons, NeighborhoodsJoiners.equal(TargetCell::dayKey,
                        KempeCellNeighborhoodProvider::lessonDayKey));

        // (2a) every date's timeslots, grouped by date (the cell's own date has no lessons,
        // so its consecutive slots must come from the fact side)
        UniEnumeratingStream<SchoolSchedule, SlotBag> slotBags = slots
                .groupBy((UniNeighborhoodsMapper<SchoolSchedule, Timeslot, String>)
                        (view, t) -> t.getDate().toString(), NeighborhoodsCollectors.toList())
                .map((BiNeighborhoodsMapper<SchoolSchedule, String, List<Timeslot>, SlotBag>)
                        (view, dateKey, list) -> new SlotBag(dateKey, list));

        // (2b) cell x its group's lessons, grouped into one bag per cell
        UniEnumeratingStream<SchoolSchedule, CellLessonsBag> cellBags = cells.join(lessons,
                NeighborhoodsJoiners.equal(TargetCell::groupKey,
                        KempeCellNeighborhoodProvider::lessonGroupKey))
                .map((BiNeighborhoodsMapper<SchoolSchedule, TargetCell, Lesson, CellLesson>)
                        (view, cell, lesson) -> new CellLesson(cell, lesson))
                .groupBy((UniNeighborhoodsMapper<SchoolSchedule, CellLesson, TargetCell>)
                        (view, cl) -> cl.cell(),
                        NeighborhoodsCollectors.toList(
                                (UniNeighborhoodsMapper<SchoolSchedule, CellLesson, Lesson>)
                                        (view, cl) -> cl.lesson()))
                .map((BiNeighborhoodsMapper<SchoolSchedule, TargetCell, List<Lesson>, CellLessonsBag>)
                        (view, cell, list) -> new CellLessonsBag(cell, list));

        // (3) join the cell-date slots and plan the whole-cell transaction
        // (the terminal lives on the sampling side: pick(...).pick(...).asMove(...))
        return factory.pick(cellBags)
                .pick(slotBags, NeighborhoodsJoiners.equal(CellLessonsBag::dateKey, SlotBag::dateKey))
                .asMove((solution, bag, bagSlots) -> KempeCellMove.plan(solution, timeslotVar,
                        bag.cell, bag.groupLessons, bagSlots.slots));
    }

    // ─── static helpers ──────────────────────────────────────────────────────

    static String lessonDayKey(Lesson l) {
        return l.course != null && l.course.studentGroup != null && l.timeslot != null
                ? l.course.studentGroup.id + "@" + l.timeslot.getDate()
                : "unplaced";
    }

    static Long lessonGroupKey(Lesson l) {
        return l.course != null && l.course.studentGroup != null ? l.course.studentGroup.id : null;
    }

    private static int declaredQuota(Long groupId, LocalDate date) {
        var row = SchoolSchedule.getExpectedHoursRow(groupId, date);
        return row == null ? 0 : row.aulaHours + row.labHours;
    }

    /**
     * True when {@code t} is the first grid slot of {@code date} inside the group's calendar
     * window AND the date carries an in-scope declared quota row. The 2026/27 grid has all
     * 8 slots on every date (verified: 174 dates x 8), so the head is exactly windowMin.
     */
    private static boolean isDeclaredFirstOpenSlot(StudentGroup g, Timeslot t) {
        if (declaredQuota(g.id, t.getDate()) <= 0) {
            return false;
        }
        var rules = SchoolSchedule.getGroupCalendarRulesForGroup(g.id);
        int windowMin = 0;
        if (rules != null) {
            int min = Integer.MAX_VALUE;
            boolean any = false;
            for (var r : rules) {
                if (r.dayOfWeek == t.getDate().getDayOfWeek()) {
                    min = Math.min(min, r.minSlot);
                    any = true;
                }
            }
            if (any) {
                windowMin = min;
            }
        }
        return TimeslotSequence.slotIndexOf(t) == windowMin;
    }

    // ─── the move ─────────────────────────────────────────────────────────────

    /**
     * A planned whole-cell transaction: parallel lists of (lesson, target timeslot). The
     * plan is computed against the CURRENT state; the stream machinery re-maps on solution
     * changes, so stale plans are not reused.
     */
    static final class KempeCellMove implements Move<SchoolSchedule> {

        private final PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot> timeslotVar;
        private final List<Lesson> lessons;
        private final List<Timeslot> targets;

        private KempeCellMove(PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot> timeslotVar,
                              List<Lesson> lessons, List<Timeslot> targets) {
            this.timeslotVar = timeslotVar;
            this.lessons = lessons;
            this.targets = targets;
        }

        /**
         * Plans the transaction EDF-style: fill the cell contiguously from its first slot
         * with tail hours from surplus donor days (each donor keeps
         * {@code max(own quota, practiced floor)}), sliding the donor hours above each
         * extraction down one slot. Donor-date slots for the slides come from the OTHER
         * lessons' current timeslots when available. Returns a no-op when the quota is
         * unreachable (the H32 valley law: partial fills are never offered).
         */
        static KempeCellMove plan(SolutionView<SchoolSchedule> view,
                                  PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot> timeslotVar,
                                  TargetCell cell, List<Lesson> groupLessons, List<Timeslot> cellDateSlots) {
            Map<LocalDate, TreeMap<Integer, Lesson>> dayLessons = new HashMap<>();
            Map<LocalDate, Map<Integer, Timeslot>> slotsByDate = new HashMap<>();
            for (Timeslot ts : cellDateSlots) {
                slotsByDate.computeIfAbsent(ts.getDate(), d -> new HashMap<>())
                        .put(TimeslotSequence.slotIndexOf(ts), ts);
            }
            for (Lesson l : groupLessons) {
                if (l.timeslot == null || l.pinned) {
                    continue;
                }
                var t = l.getTeacher();
                if (t != null && t.pseudo) {
                    continue;
                }
                dayLessons.computeIfAbsent(l.timeslot.getDate(), d -> new TreeMap<>())
                        .put(TimeslotSequence.slotIndexOf(l.timeslot), l);
                // every placed lesson contributes its own timeslot to the slot index:
                // donor-date slots for the slides come from these
                slotsByDate.computeIfAbsent(l.timeslot.getDate(), d -> new HashMap<>())
                        .put(TimeslotSequence.slotIndexOf(l.timeslot), l.timeslot);
            }
            Integer floor = SchoolSchedule.getDayFloor(cell.group().id);
            int practicedFloor = floor != null ? floor : 0;

            List<Lesson> movers = new ArrayList<>();
            List<Timeslot> landings = new ArrayList<>();
            int nextSlot = TimeslotSequence.slotIndexOf(cell.firstSlot());
            int placed = 0;
            while (placed < cell.quota() && nextSlot <= 7) {
                Timeslot targetTs = slotsByDate.getOrDefault(cell.date(), Map.of()).get(nextSlot);
                if (targetTs == null) {
                    break;
                }
                int before = movers.size();
                if (!extractOneHour(cell, dayLessons, practicedFloor, targetTs, slotsByDate,
                        movers, landings)) {
                    break;
                }
                placed += movers.size() - before > 0 ? 1 : 0;
                if (movers.size() == before) {
                    break;
                }
                nextSlot++;
            }
            if (placed < cell.quota()) {
                return new KempeCellMove(timeslotVar, List.of(), List.of()); // valley-safe no-op
            }
            return new KempeCellMove(timeslotVar, movers, landings);
        }

        /**
         * Extracts one hour for the target slot from a surplus donor day (tail extraction
         * with slide-down), appending the extraction and its slides to the movers/landings.
         */
        private static boolean extractOneHour(TargetCell cell,
                                              Map<LocalDate, TreeMap<Integer, Lesson>> dayLessons,
                                              int practicedFloor, Timeslot targetTs,
                                              Map<LocalDate, Map<Integer, Timeslot>> slotsByDate,
                                              List<Lesson> movers, List<Timeslot> landings) {
            for (var donor : dayLessons.entrySet()) {
                if (donor.getKey().equals(cell.date())) {
                    continue;
                }
                TreeMap<Integer, Lesson> day = donor.getValue();
                if (day.isEmpty()) {
                    continue;
                }
                int donorQuota = declaredQuota(cell.group().id, donor.getKey());
                if (day.size() - 1 < Math.max(donorQuota, practicedFloor)) {
                    continue; // gate (e): the donor keeps its quota/floor
                }
                for (int slot : new ArrayList<>(day.keySet())) {
                    Lesson l = day.get(slot);
                    if (l == null) {
                        continue;
                    }
                    List<Lesson> slideMovers = new ArrayList<>();
                    List<Timeslot> slideTargets = new ArrayList<>();
                    boolean legal = true;
                    for (int j : new ArrayList<>(day.tailMap(slot, false).keySet())) {
                        Lesson mover = day.get(j);
                        Timeslot down = slotsByDate.getOrDefault(donor.getKey(), Map.of()).get(j - 1);
                        if (mover == null || down == null) {
                            legal = false;
                            break;
                        }
                        slideMovers.add(mover);
                        slideTargets.add(down);
                    }
                    if (!legal) {
                        continue;
                    }
                    movers.add(l);
                    landings.add(targetTs);
                    movers.addAll(slideMovers);
                    landings.addAll(slideTargets);
                    // apply locally so the next hour sees the updated donor
                    day.remove(slot);
                    for (int j : new ArrayList<>(day.tailMap(slot, false).keySet())) {
                        day.put(j - 1, day.remove(j));
                    }
                    return true;
                }
            }
            return false;
        }

        @Override
        public void execute(MutableSolutionView<SchoolSchedule> solutionView) {
            for (int i = 0; i < lessons.size(); i++) {
                solutionView.changeVariable(timeslotVar, lessons.get(i), targets.get(i));
            }
        }

        @Override
        public Move<SchoolSchedule> rebase(Lookup lookup) {
            List<Lesson> rebased = new ArrayList<>(lessons.size());
            for (Lesson l : lessons) {
                Lesson working = lookup.lookUpWorkingObject(l);
                if (working == null) {
                    return new KempeCellMove(timeslotVar, List.of(), List.of());
                }
                rebased.add(working);
            }
            return new KempeCellMove(timeslotVar, rebased, targets);
        }

        @Override
        public SequencedCollection<Object> getPlanningEntities() {
            return new ArrayList<>(lessons);
        }

        @Override
        public SequencedCollection<Object> getPlanningValues() {
            return new ArrayList<>(targets);
        }
    }
}
