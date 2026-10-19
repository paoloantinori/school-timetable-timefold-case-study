package com.school.planner.solver;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.Room;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Timeslot;
import com.school.planner.domain.constraints.DayStartOffsetJustification;
import com.school.planner.domain.constraints.StudentGroupDailyHoursJustification;
import com.school.planner.domain.constraints.TeacherDailyHoursJustification;
import com.school.planner.service.ScheduleService;
import com.school.planner.util.SubjectNormalization;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.impl.score.constraint.ConstraintMatchPolicy;
import ai.timefold.solver.core.impl.score.director.ScoreDirectorFactory;
import ai.timefold.solver.core.impl.solver.DefaultSolutionManager;

/**
 * TASK-638: the violation-directed steepest-descent finisher that Timefold
 * community does NOT have. Their move selectors enumerate static entity/value
 * lists for performance (violation-direction costs one score per proposal);
 * at delivery time, on a small residual, we can afford it.
 *
 * <p>THE MECHANISM: score with constraint matches, take the lessons justified
 * by the hard-negative matches, generate mutations ONLY around those targets
 * (change-timeslot + same-group swap), pick the best hard-improving one
 * (steepest), apply, iterate until no improvement or cap.
 *
 * <p>DETERMINISM: no RNG. Mutation generation and selection are fully ordered
 * (deterministic entity ordering by id, steepest with deterministic
 * tie-break by delta then by first-generated). The oracle property holds:
 * only hard-improving mutations are applied.
 *
 * <p>SCOPE: proposal only, never evaluation. Every candidate is evaluated by
 * the FULL constraint provider (all families, all tiers). No gate is
 * bypassed. Pinned lessons are never mutated. The belt at the end re-scores
 * and reports the hard total.
 *
 * <p>EVIDENCE (why this shape): the A/B 637 near-misses showed two
 * COMPOSITE_MISSING cells from two different families, each closable with a
 * single mutation that no fixed-grammar arm finds. The 638b bench proved the
 * statistical ceiling (deterministic LS closes the -2 in 303s); this aims
 * for the same result in seconds by starting from the wound.
 *
 * <p>ROOM-AWARE MODE (TASK-641, opt-in strict-literal): when armed AND the
 * plain space is exhausted, composite candidates (timeslot move + room
 * reassignment) compete. This is a DELIBERATE exception to pure steepest
 * descent over the armed space: composites are never tried while a plain
 * candidate improves, so a strictly better composite can be missed when a
 * plain cure exists. The trade buys byte-identical behavior whenever the
 * plain grammar suffices (clean bench attribution of the extension) at the
 * cost of that corner; {@code maxCandidates} caps each population
 * separately, so an armed iteration can evaluate up to 3x the knob.
 */
public final class ViolationScopedSteepestFinisher {

    private static final Logger LOG =
            Logger.getLogger(ViolationScopedSteepestFinisher.class);

    private ViolationScopedSteepestFinisher() {
    }

    /** The census line for the log. */
    public record Stats(int movesEvaluated, int movesApplied,
            int hardBefore, int hardAfter, long wallMillis) {
    }

    /**
     * Production gate (RATIFIED DEFAULT ON, b90929d2): null/absent = ON, only
     * the literal {@code false} disarms (the family's opt-out standard; the
     * ratified extensions below share it). The bench calls {@link #finish}
     * directly and is NOT gated by this.
     */
    public static boolean enabled() {
        String v = System.getProperty("planner.construction.violation-steepest.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /**
     * ROOM-AWARE extension (TASK-641, RATIFIED DEFAULT ON 23/09, delegated
     * ratification on complete evidence: A/B 5/5 with declared no-op parity
     * on fresh cycles + positive banco delta on catalog states + all gates):
     * composite candidates that also REASSIGN the room of the moved lesson(s).
     * The base grammar never touches {@code room}, so a move whose timeslot
     * side cures the wound but whose carried rooms collide at the destination
     * (H2) or land null (H29 on a boxed lesson) is refused by the oracle and
     * the wound stays open (integrato-1 H28: the swap closes the pattern but
     * the carried room collides at the new slot). LAZY second pass: variants
     * are generated only when NO plain candidate improves, so whenever a
     * plain cure exists the base surface runs byte-identical. Opt-out
     * standard: null/absent = ON, only the literal "false" disarms.
     */
    public static boolean roomAwareEnabled() {
        String v = System.getProperty(
                "planner.construction.violation-steepest.room-aware.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /**
     * UNBLOCK CROSS-GROUP extension (TASK-657, RATIFIED DEFAULT ON 23/09,
     * maintainer: bounded-no-op insurance - the class is real even though the one
     * basin that spawned it is structurally blocked by teacher vacations, so
     * the arm has no demonstrated positive yet; the oracle bounds it, a
     * different GRASP basin may trigger it; precedent CSF TASK-609):
     * composite chains that free the teacher blocking a tail slide. Shape
     * (the cieco-v3 class): the wounded day's LAST lesson would slide into
     * the day's internal hole but its teacher is busy at the hole slot
     * teaching ANOTHER group; a cell-for-cell cross-date swap inside THAT
     * group (blocker exchanged with a same-group partner on another day,
     * counts invariant, contiguity preserved by construction, colliding
     * carried rooms deterministically reassigned) vacates the teacher; the
     * tail then slides. Neither HRF (same-group only) nor the base/room-aware
     * grammar (same-group swaps, violation-scoped targets) expresses it: the
     * cure lives on lessons of a group that has no violation at all.
     * Standard opt-out: null/absent = ON, only the literal "false" disarms.
     */
    public static boolean unblockCrossGroupEnabled() {
        String v = System.getProperty(
                "planner.construction.violation-steepest.unblock.enabled");
        return v == null || Boolean.parseBoolean(v);
    }

    /**
     * Bench form: builds its own score director factory (full constraint set),
     * then delegates. The problem is mutated in place.
     *
     * @param problem the delivered state (mutated)
     * @param maxIterations safety cap
     * @param maxCandidates safety cap on mutation generation per iteration
     */
    public static Stats finish(SchoolSchedule problem,
            int maxIterations, int maxCandidates) {
        SolverConfig cfg = new SolverConfig()
                .withSolutionClass(SchoolSchedule.class)
                .withEntityClasses(Lesson.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(SchoolScheduleConstraintProvider.class));
        @SuppressWarnings("unchecked")
        SolutionManager<SchoolSchedule, BendableScore> manager =
                SolutionManager.create(SolverFactory.create(cfg));
        var directorFactory = ((DefaultSolutionManager<SchoolSchedule, BendableScore>) manager)
                .getScoreDirectorFactory();
        return finish(problem, directorFactory, maxIterations, maxCandidates);
    }

    /**
     * Production form: takes the SHARED score director factory (the cycle's
     * cached one, {@code ScheduleService.getScoreDirectorFactory()}) instead of
     * building a constraint set per call; the evaluation directors stay
     * fresh-per-pass like every sibling's snapshot director.
     */
    public static Stats finish(SchoolSchedule problem,
            ScoreDirectorFactory<SchoolSchedule, BendableScore> directorFactory,
            int maxIterations, int maxCandidates) {

        long t0 = System.currentTimeMillis();
        int totalEvaluated = 0;
        int totalApplied = 0;

        try (var director = directorFactory.createScoreDirectorBuilder()
                .withConstraintMatchPolicy(ConstraintMatchPolicy.ENABLED)
                .withLookUpEnabled(false)
                .withExpectShadowVariablesInCorrectState(false)
                .build()) {
            director.setWorkingSolution(problem);
            // The metamodel is resolved ONCE: Moves.change/swap built on it are
            // what the incremental engine actually sees (raw field writes are
            // invisible to bavet, the 638 WIP lesson). rc-1 exposes it only via
            // getMetaModel() navigation; the typed accessor does not exist, so a
            // downcast from VariableMetaModel is the sanctioned route (a list
            // variable would fail the cast loudly, which we want).
            var timeslotVar = variableMetaModel(director, "timeslot", Timeslot.class);
            // resolved unconditionally: a microsecond navigation, and the
            // unarmed path never sees it (variant generation, the only
            // consumer of room moves, is gated by roomAwareEnabled below)
            var roomVar = variableMetaModel(director, "room", Room.class);

            // byId and byGroup are loop-invariant: lesson identity and group
            // membership never change during the walk (only timeslots move).
            // The DAY indexes are NOT: an applied move changes a lesson's day
            // membership, so they are rebuilt at every iteration top (code
            // review F1: a stale cell misses lessons that moved INTO a wound
            // day and mis-directs the ones that moved OUT).
            Map<Long, Lesson> byId = new HashMap<>();
            Map<Long, List<Lesson>> byGroup = new HashMap<>();
            for (Lesson l : problem.getLessons()) {
                byId.put(l.id, l);
                if (l.timeslot != null && l.course != null
                        && l.course.studentGroup != null && !l.pinned) {
                    byGroup.computeIfAbsent(l.course.studentGroup.id, k -> new ArrayList<>()).add(l);
                }
            }

            int hardBefore = hardOf(director);

            for (int iter = 0; iter < maxIterations; iter++) {
                // one score per iteration feeds both the constraint matches
                // (collectTargets) and the evaluation baseline: nothing between
                // here and the candidate loop mutates the solution
                var baseline = ScoreCompat.hardLevels((BendableScore) director.calculateScore().raw());
                // rebuilt per iteration (see the comment above the loop): the
                // custom-justification families (H7 teacher-day, H11/H31
                // group-day) and the H28 context scan read DAY MEMBERSHIP
                Map<Long, Map<LocalDate, List<Lesson>>> byTeacherDay = new HashMap<>();
                Map<Long, Map<LocalDate, List<Lesson>>> byGroupDay = new HashMap<>();
                for (Lesson l : problem.getLessons()) {
                    if (l.timeslot == null || l.course == null) {
                        continue;
                    }
                    if (l.course.teacher != null) {
                        byTeacherDay.computeIfAbsent(l.course.teacher.id, k -> new HashMap<>())
                                .computeIfAbsent(l.timeslot.getDate(), k -> new ArrayList<>()).add(l);
                    }
                    if (l.course.studentGroup != null) {
                        byGroupDay.computeIfAbsent(l.course.studentGroup.id, k -> new HashMap<>())
                                .computeIfAbsent(l.timeslot.getDate(), k -> new ArrayList<>()).add(l);
                    }
                }
                List<Lesson> targets = collectTargets(director, byId, byTeacherDay, byGroupDay);
                if (targets.isEmpty()) {
                    break;
                }
                List<Candidate> candidates = generateCandidates(problem, targets, maxCandidates, byGroup);
                if (candidates.isEmpty()) {
                    break;
                }
                // steepest on the LEXICOGRAPHIC hard pair (pure, quasi): the
                // score's own ordering; a level-0-only oracle would trade pure
                // hard for unbounded quasi damage under the quasi-tier flag.
                // Ties keep the first-generated candidate (the evaluation loop
                // follows generation order, so that is the invariant).
                Candidate best = steepest(director, timeslotVar, roomVar, candidates, baseline);
                // ROOM-AWARE lazy pass (TASK-641): the plain space is exhausted,
                // re-try the same moves with fresh rooms for the moved lessons.
                // Free-at-destination filtering keeps the cross product tiny;
                // the full-constraint oracle keeps the final word.
                int roomVariants = 0;
                if (best == null && roomAwareEnabled()) {
                    List<Candidate> variants = generateRoomVariants(problem, candidates, maxCandidates);
                    roomVariants = variants.size();
                    best = steepest(director, timeslotVar, roomVar, variants, baseline);
                }
                // UNBLOCK CROSS-GROUP lazy pass (TASK-657): both grammars above
                // are exhausted; the wound may still be closable by freeing the
                // teacher that blocks the tail slide, via a same-group swap in
                // the blocker's group. Prototype strict-literal.
                int unblockVariants = 0;
                if (best == null && unblockCrossGroupEnabled()) {
                    List<Candidate> unblocked = generateUnblockCandidates(problem, targets,
                            byGroup, byGroupDay, maxCandidates);
                    unblockVariants = unblocked.size();
                    best = steepest(director, timeslotVar, roomVar, unblocked, baseline);
                }
                totalEvaluated += candidates.size() + roomVariants + unblockVariants;
                if (best == null) {
                    int zero = 0, neg = 0;
                    for (Candidate c : candidates) {
                        int cmp = c.delta.compareTo(ScoreCompat.ZERO_HARD_LEVELS);
                        if (cmp == 0) zero++;
                        else if (cmp < 0) neg++;
                    }
                    // the zero/neg census covers the PLAIN population; the
                    // variant population is reported by count only (its
                    // deltas are all non-improving by construction here)
                    LOG.infof("  no improving candidate in %d plain + %d room-variant + %d unblock evaluated"
                            + " (zero=%d negative=%d, best plain delta %s)",
                            candidates.size(), roomVariants, unblockVariants, zero, neg,
                            candidates.stream().map(c -> c.delta).max(ScoreCompat.HardLevels::compareTo).get());
                    break;
                }
                apply(director, timeslotVar, roomVar, best);
                totalApplied++;
            }

            director.calculateScore();
            int hardAfter = hardOf(director);

            long wall = System.currentTimeMillis() - t0;
            LOG.infof("ViolationScopedSteepestFinisher (TASK-638): applied=%d evaluated=%d"
                    + " hard %d -> %d in %dms",
                    totalApplied, totalEvaluated, hardBefore, hardAfter, (int) wall);
            return new Stats(totalEvaluated, totalApplied,
                    hardBefore, hardAfter, wall);
        }
    }

    private static int hardOf(ai.timefold.solver.core.impl.score.director.InnerScoreDirector<SchoolSchedule, BendableScore> director) {
        return (int) ScoreCompat
                .hardOf((BendableScore) director.calculateScore().raw());
    }

    // ── Target collection (violation-directed) ─────────────────────────────

    /**
     * Targets are the lessons justified by every hard-negative match. Two
     * justification shapes exist: fact-bearing (default + LessonBearing, e.g.
     * H1/H2/H3) which {@link ScheduleService#resolveLessons} already handles,
     * and ID-bearing custom records (H7 teacher-day, H11/H31 group-day) which
     * must be resolved through the day indexes; reading only the default shape
     * leaves six hard families - including the H7 this finisher exists for -
     * with zero targets (code-review F1, PASSO 7).
     */
    private static List<Lesson> collectTargets(
            ai.timefold.solver.core.impl.score.director.InnerScoreDirector<SchoolSchedule, BendableScore> director,
            Map<Long, Lesson> byId,
            Map<Long, Map<LocalDate, List<Lesson>>> byTeacherDay,
            Map<Long, Map<LocalDate, List<Lesson>>> byGroupDay) {
        Map<Lesson, Integer> counts = new LinkedHashMap<>();
        for (var entry : director.getConstraintMatchTotalMap().entrySet()) {
            for (var match : entry.getValue().getConstraintMatchSet()) {
                if (ScoreCompat.hardLevels((BendableScore) match.getScore())
                        .compareTo(ScoreCompat.ZERO_HARD_LEVELS) >= 0) {
                    continue;
                }
                List<Lesson> wound = resolveTargetLessons(match.getJustification(), byId,
                        byTeacherDay, byGroupDay);
                for (Lesson lesson : wound) {
                    counts.merge(lesson, 1, Integer::sum);
                }
                // CONTEXT lessons (the blind-puzzle discovery, H28 G13 12/02):
                // the match's facts carry only the pattern's endpoints, but the
                // cure may live on the MIDDLE lesson. For every same-group,
                // same-teacher, same-subject, same-day pair in the wound (the
                // A..B..A shape, H28 today and any family with the same
                // pattern), also target the different-teacher same-subject
                // lessons strictly between them: the arbitrated cure (M-Lav@9
                // <-> M-TS@12) moves the "c", not the pair, and without this
                // it is never proposed.
                addContextTargets(wound, byGroupDay, counts);
            }
        }
        List<Lesson> result = new ArrayList<>(counts.keySet());
        result.sort((a, b) -> {
            int byCount = Integer.compare(counts.get(b), counts.get(a));
            return byCount != 0 ? byCount : Long.compare(a.id, b.id);
        });
        return result;
    }

    /**
     * Adds the pattern's middle lessons as targets: for each same-teacher,
     * same-subject, same-day pair (x,y) in the wound, every lesson of that
     * (group, subject, day) strictly between x and y with a different teacher.
     * The pair/between semantics are the provider's own gates (h28ReturnPair /
     * h28Between, shared with H28RotationFinisher per their javadoc: the stream
     * and the deterministic pass must read the same A..B..A semantics); the
     * group/subject/date equalities here mirror the constraint's joiners.
     */
    static void addContextTargets(List<Lesson> wound,
            Map<Long, Map<LocalDate, List<Lesson>>> byGroupDay,
            Map<Lesson, Integer> counts) {
        for (int i = 0; i < wound.size(); i++) {
            for (int j = 0; j < wound.size(); j++) {
                if (i == j) {
                    continue;
                }
                Lesson x = wound.get(i), y = wound.get(j);
                if (x.timeslot == null || y.timeslot == null || x.course == null
                        || y.course == null
                        || x.course.studentGroup == null || y.course.studentGroup == null
                        || x.course.subject == null || y.course.subject == null
                        || !x.course.studentGroup.id.equals(y.course.studentGroup.id)
                        || !x.course.subject.equals(y.course.subject)
                        || !x.timeslot.getDate().equals(y.timeslot.getDate())
                        || !SchoolScheduleConstraintProvider.h28ReturnPair(x, y)) {
                    continue;
                }
                for (Lesson c : byGroupDay.getOrDefault(x.course.studentGroup.id, Map.of())
                        .getOrDefault(x.timeslot.getDate(), List.of())) {
                    // x's subject is null-guarded above; the null-safe direction
                    // keeps a null-subject c (a course column with no @NotNull)
                    // from NPE-ing the walk
                    if (c.course != null && x.course.subject.equals(c.course.subject)
                            && SchoolScheduleConstraintProvider.h28Between(x, c, y)) {
                        counts.merge(c, 1, Integer::sum);
                    }
                }
            }
        }
    }

    private static List<Lesson> resolveTargetLessons(Object justification, Map<Long, Lesson> byId,
            Map<Long, Map<LocalDate, List<Lesson>>> byTeacherDay,
            Map<Long, Map<LocalDate, List<Lesson>>> byGroupDay) {
        List<Lesson> resolved = ScheduleService.resolveLessons(justification, byId);
        if (!resolved.isEmpty()) {
            return resolved;
        }
        // custom ID-bearing justifications: the wound is the teacher's/group's
        // whole day; every lesson of that cell is a legitimate mutation target
        if (justification instanceof TeacherDailyHoursJustification j) {
            return byTeacherDay.getOrDefault(j.teacherId(), Map.of())
                    .getOrDefault(j.date(), List.of());
        }
        if (justification instanceof StudentGroupDailyHoursJustification j) {
            return byGroupDay.getOrDefault(j.groupId(), Map.of())
                    .getOrDefault(j.date(), List.of());
        }
        if (justification instanceof DayStartOffsetJustification j) {
            return byGroupDay.getOrDefault(j.groupId(), Map.of())
                    .getOrDefault(j.date(), List.of());
        }
        // generic (StudentGroup, LocalDate, ...) tuples: H17's justification is
        // (group, date, break) - the wound is the group's whole day, exactly
        // like the ID-bearing records above (same F1 class, found by TASK-657's
        // banco: an H17-only state produced ZERO targets and the walk died
        // before evaluating anything)
        if (justification instanceof ai.timefold.solver.core.api.score.stream.DefaultConstraintJustification dj) {
            com.school.planner.domain.StudentGroup g = null;
            LocalDate d = null;
            for (Object f : dj.getFacts()) {
                if (f instanceof StudentGroup sg) {
                    g = sg;
                } else if (f instanceof LocalDate ld) {
                    d = ld;
                }
            }
            if (g != null && d != null) {
                return byGroupDay.getOrDefault(g.id, Map.of()).getOrDefault(d, List.of());
            }
        }
        return List.of();
    }

    // ── Candidate generation (scoped to targets, deterministic) ────────────

    private static final class Candidate {
        final Lesson lesson;
        final Timeslot newTimeslot;  // null = swap
        final Lesson swapPartner;    // null = change
        // TASK-641 room-aware: fresh rooms for the moved lessons (null = keep
        // the carried one); the move is the timeslot move COMPOSED with room
        // changes, both incrementally visible to the engine
        Room roomForLesson;
        Room roomForPartner;
        // TASK-657 unblock cross-group (all three null unless isUnblock()):
        // the tail slide (lesson -> newTimeslot, same day) is composed with a
        // cell-for-cell cross-date swap inside the BLOCKER's group
        // (blocker <-> blockerPartner) that frees the teacher at the hole slot
        Lesson blocker;
        Lesson blockerPartner;
        ScoreCompat.HardLevels delta;

        Candidate(Lesson l, Timeslot ts, Lesson partner) {
            this(l, ts, partner, null, null, null, null);
        }

        Candidate(Lesson l, Timeslot ts, Lesson partner,
                Room roomForLesson, Room roomForPartner) {
            this(l, ts, partner, roomForLesson, roomForPartner, null, null);
        }

        /** Factory, not a constructor: a 7-arg unblock ctor would be ambiguous
         *  against the full private one at every null-bearing delegation site. */
        static Candidate unblock(Lesson tail, Timeslot hole, Lesson blocker, Lesson blockerPartner,
                Room roomForBlocker, Room roomForPartner, Room roomForTail) {
            Candidate c = new Candidate(tail, hole, null, null, null, null, null);
            c.blocker = blocker;
            c.blockerPartner = blockerPartner;
            c.roomForLesson = roomForBlocker;
            c.roomForPartner = roomForPartner;
            c.roomForTail = roomForTail;
            return c;
        }

        /** TASK-657 review F1: fresh room for the tail at the hole (null = keep carried). */
        Room roomForTail;

        private Candidate(Lesson l, Timeslot ts, Lesson partner,
                Room roomForLesson, Room roomForPartner, Lesson blocker, Lesson blockerPartner) {
            this.lesson = l;
            this.newTimeslot = ts;
            this.swapPartner = partner;
            this.roomForLesson = roomForLesson;
            this.roomForPartner = roomForPartner;
            this.blocker = blocker;
            this.blockerPartner = blockerPartner;
        }

        boolean isSwap() {
            return swapPartner != null;
        }

        boolean isUnblock() {
            return blocker != null;
        }
    }

    /**
     * Steepest over one population: every candidate is evaluated and the best
     * hard-improving one wins, ties keep the first-generated (the determinism
     * invariant of the class). Shared by the plain pass and the lazy
     * room-aware pass so the selection rule lives in exactly one place.
     */
    private static Candidate steepest(
            ai.timefold.solver.core.impl.score.director.InnerScoreDirector<SchoolSchedule, BendableScore> director,
            ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot> timeslotVar,
            ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel<SchoolSchedule, Lesson, Room> roomVar,
            List<Candidate> cands, ScoreCompat.HardLevels baseline) {
        Candidate best = null;
        boolean dbg = Boolean.getBoolean("planner.construction.violation-steepest.unblock.debug");
        for (Candidate cand : cands) {
            cand.delta = evaluate(director, timeslotVar, roomVar, cand, baseline);
            if (cand.isUnblock() && dbg) {
                LOG.infof("  [unblock-debug] tail=%s@%s blocker=%s(%s) m=%s delta=%s",
                        cand.lesson.course.subject, cand.newTimeslot.getDate(),
                        cand.blocker.course.subject, cand.blocker.course.studentGroup.name,
                        cand.blockerPartner.course.subject, cand.delta);
            }
            if (cand.delta.compareTo(ScoreCompat.ZERO_HARD_LEVELS) > 0
                    && (best == null || cand.delta.compareTo(best.delta) > 0)) {
                best = cand;
            }
        }
        return best;
    }

    /**
     * Room-reassignment variants of the plain candidates (TASK-641). For a
     * SWAP each side may keep its carried room (null included: an H29-style
     * unassigned side legitimately stays null) or take one free at its
     * destination slot; for a CHANGE the moved lesson may take one free at
     * the destination. Free = no lesson holds that room at that slot, or only
     * the one that is about to vacate it (the swap partner). DECLARED BLIND
     * SPOT (TASK-652 owns the family fix): this probe is tag-blind to the H2
     * co-presenza exemption, so a legal golden-depth shared room is never
     * PROPOSED; the oracle accepts everything legal among what is proposed.
     * The VRP ({@link Lesson#getAvailableRooms}) already filters by required
     * tags so H-M2 legality is structural; the oracle still sees everything
     * else (H2/H29/M3/M-LF included). Rooms are ordered by id: determinism.
     * The cap applies to THIS population on top of the plain one (an armed
     * iteration can evaluate up to 2x {@code maxCandidates}), distributed
     * with a per-plain quota first and the leftover budget after, so a
     * swap-heavy prefix cannot starve the tail (the generateCandidates
     * fairness invariant).
     */
    private static List<Candidate> generateRoomVariants(SchoolSchedule problem,
            List<Candidate> plains, int max) {
        List<Candidate> list = new ArrayList<>();
        // Set form: the probe is membership, not a scan. Built once per lazy
        // pass and dies with it (applied moves change occupancy).
        Map<Long, Set<Long>> occupiedRoomsBySlot =
                RoomAvailability.occupiedRoomIdsBySlot(problem.getLessons());
        // sorted-once view of each lesson's value range (cached and immutable
        // per lesson, so the sort is pure repetition after the first call)
        Map<Lesson, List<Room>> sortedRoomsByLesson = new IdentityHashMap<>();
        Set<PairKey> expandedPairs = new HashSet<>();
        int quota = Math.max(1, max / Math.max(1, plains.size()));
        // pass 0: every plain contributes up to its quota; pass 1: the
        // leftover budget reopens the plains in order
        for (int pass = 0; pass < 2 && list.size() < max; pass++) {
            int bound = pass == 0 ? quota : Integer.MAX_VALUE;
            for (Candidate plain : plains) {
                if (list.size() >= max) {
                    break;
                }
                expandRoomVariants(plain, bound, list, max,
                        occupiedRoomsBySlot, sortedRoomsByLesson, expandedPairs);
            }
        }
        return list;
    }

    /** Adds up to {@code bound} room variants of one plain candidate. */
    private static void expandRoomVariants(Candidate plain, int bound, List<Candidate> list, int max,
            Map<Long, Set<Long>> occupiedRoomsBySlot,
            Map<Lesson, List<Room>> sortedRoomsByLesson, Set<PairKey> expandedPairs) {
        int added = 0;
        if (plain.isSwap()) {
            Lesson a = plain.lesson;
            Lesson b = plain.swapPartner;
            // DEDUP THE UNORDERED PAIR (code-review F1): the plain list emits
            // Candidate(target, partner) from the target side only, so the
            // reverse direction exists IFF the partner is itself a target; an
            // id-order filter would silently drop the ONLY direction of every
            // target-vs-non-target pair whose target has the higher id.
            if (!expandedPairs.add(new PairKey(Math.min(a.id, b.id), Math.max(a.id, b.id)))) {
                return;
            }
            List<Room> roomsA = freeRoomsAt(a, b.timeslot, occupiedRoomsBySlot, b.room, sortedRoomsByLesson);
            List<Room> roomsB = freeRoomsAt(b, a.timeslot, occupiedRoomsBySlot, a.room, sortedRoomsByLesson);
            for (Room ra : withCarried(a.room, roomsA)) {
                for (Room rb : withCarried(b.room, roomsB)) {
                    if (added >= bound || list.size() >= max) {
                        return;
                    }
                    boolean keepA = sameRoom(ra, a.room);
                    boolean keepB = sameRoom(rb, b.room);
                    if (keepA && keepB) {
                        continue; // the plain combination, already evaluated
                    }
                    list.add(new Candidate(a, null, b, keepA ? null : ra, keepB ? null : rb));
                    added++;
                }
            }
        } else {
            List<Room> rooms = freeRoomsAt(plain.lesson, plain.newTimeslot,
                    occupiedRoomsBySlot, null, sortedRoomsByLesson);
            for (Room r : rooms) {
                if (added >= bound || list.size() >= max) {
                    return;
                }
                list.add(new Candidate(plain.lesson, plain.newTimeslot, null, r, null));
                added++;
            }
        }
    }

    /** Unordered lesson-id pair, the dedup key of the swap variant space. */
    private record PairKey(long lo, long hi) {
    }

    /** Sentinel: the carried room collides at the landing and no alternative exists. */
    private static final Room NO_ROOM = new Room();

    /**
     * TASK-657: if the lesson's carried room is held at dest by anyone other
     * than the vacating partner, the first (by id) free room of its value
     * range; null if the carried room needs no change; {@link #NO_ROOM} if it
     * collides and no alternative exists. Membership probes on the same
     * occupancy index the room-aware pass uses (built once per pass).
     */
    private static Room freshRoomIfCarriedCollides(Lesson l, Timeslot dest, Lesson vacating,
            Map<Long, Map<Long, Integer>> holdersBySlotRoom, Map<Lesson, List<Room>> sortedRoomsByLesson) {
        if (l.room == null) {
            return null;
        }
        if (!heldByOthers(dest, l.room, vacating, holdersBySlotRoom)) {
            return null;
        }
        for (Room r : sortedRoomsByLesson.computeIfAbsent(l, k -> {
            List<Room> sorted = new ArrayList<>(k.getAvailableRooms());
            sorted.sort(Comparator.comparingLong(room -> room.id));
            return sorted;
        })) {
            if (!heldByOthers(dest, r, vacating, holdersBySlotRoom)) {
                return r;
            }
        }
        return NO_ROOM;
    }

    /** Holder-count probe: busy when holders exceed the vacating partner's own one. */
    private static boolean heldByOthers(Timeslot dest, Room r, Lesson vacating,
            Map<Long, Map<Long, Integer>> holdersBySlotRoom) {
        Integer count = holdersBySlotRoom.getOrDefault(dest.id, Map.of()).get(r.id);
        if (count == null || count == 0) {
            return false;
        }
        boolean vacatingHoldsIt = vacating != null && vacating.room != null
                && vacating.room.id.equals(r.id);
        return count > (vacatingHoldsIt ? 1 : 0);
    }

    /**
     * TASK-657: cross-group unblocking chains (the cieco-v3 class). For every
     * wounded day among the targets: the internal holes, the day's TAIL lesson
     * as the only mover (the tail-slide form: vacating the last slot leaves a
     * legal trailing gap), and for each hole whose slot holds the tail's
     * teacher busy in ANOTHER group, the cell-for-cell cross-date partners of
     * that blocker inside its own group. The composite = blocker swap + tail
     * slide; the full-constraint oracle keeps the final word on everything
     * (rooms travel with the lessons: a colliding landing is simply refused).
     */
    private static List<Candidate> generateUnblockCandidates(SchoolSchedule problem,
            List<Lesson> targets, Map<Long, List<Lesson>> byGroup,
            Map<Long, Map<LocalDate, List<Lesson>>> byGroupDay, int max) {
        List<Candidate> list = new ArrayList<>();
        // wounded (group, date) KEYS from the targets, day CONTENT from the
        // shared byGroupDay index: the whole day is the wound (the resolver's
        // contract for group+date justifications), never the fact subset a
        // partial justification would hand us (a tail computed on a subset
        // would be a phantom tail)
        Map<Long, Set<LocalDate>> wounded = new LinkedHashMap<>();
        for (Lesson t : targets) {
            if (t.timeslot == null || t.course == null || t.course.studentGroup == null) {
                continue;
            }
            wounded.computeIfAbsent(t.course.studentGroup.id, k -> new LinkedHashSet<>())
                    .add(t.timeslot.getDate());
        }
        if (wounded.isEmpty()) {
            return list;
        }
        // occupancy as HOLDER COUNTS per (slot, room): the vacating partner
        // subtracts exactly one holder, so a third co-present lesson sharing
        // that room stays visible (the review F2 regression: a room-id Set
        // would have masked it and proposed a dead keep-R composite)
        Map<Long, Map<Long, Integer>> holdersBySlotRoom = new HashMap<>();
        for (Lesson l : problem.getLessons()) {
            if (l.timeslot != null && l.room != null) {
                holdersBySlotRoom.computeIfAbsent(l.timeslot.id, k -> new HashMap<>())
                        .merge(l.room.id, 1, Integer::sum);
            }
        }
        Map<Lesson, List<Room>> sortedRoomsByLesson = new IdentityHashMap<>();
        Map<LocalDate, Map<Integer, Timeslot>> slotsByDate =
                StudentDayCompaction.buildTimeslotsByDateAndSlot(problem.getTimeslots());
        for (var dayEntry : wounded.entrySet()) {
            Long groupId = dayEntry.getKey();
            for (LocalDate woundedDate : dayEntry.getValue()) {
                // whole-day membership from the shared index: any pinned lesson
                // counts as an occupant of the day, only the MOVER must be movable
                List<Lesson> day = byGroupDay.getOrDefault(groupId, Map.of())
                        .getOrDefault(woundedDate, List.of());
                if (day.size() < 2) {
                    continue;
                }
                List<Lesson> daySorted = new ArrayList<>(day);
                daySorted.sort(Comparator.comparingInt(l -> TimeslotSequence.slotIndexOf(l.timeslot)));
                Lesson tail = daySorted.get(daySorted.size() - 1);
                if (tail.course == null || tail.course.teacher == null || tail.course.teacher.pseudo) {
                    continue;
                }
                int first = TimeslotSequence.slotIndexOf(daySorted.get(0).timeslot);
                int last = TimeslotSequence.slotIndexOf(tail.timeslot);
                var occupied = new HashSet<Integer>();
                for (Lesson l : daySorted) {
                    occupied.add(TimeslotSequence.slotIndexOf(l.timeslot));
                }
                for (Timeslot hole : slotsByDate.getOrDefault(woundedDate, Map.of()).values()) {
                    if (list.size() >= max) {
                        return list;
                    }
                    int holeSlot = TimeslotSequence.slotIndexOf(hole);
                    if (holeSlot <= first || holeSlot >= last || occupied.contains(holeSlot)
                            || !tail.isSlotLegalFresh(hole)) {
                        continue;
                    }
                    // the blocker: the tail's teacher teaching another group at
                    // the hole (pinned blockers are not swappable)
                    for (Lesson b : problem.getLessons()) {
                        if (b.timeslot == null || !b.timeslot.id.equals(hole.id)) {
                            continue;
                        }
                        if (list.size() >= max) {
                            break;
                        }
                        if (b.course == null || b.course.teacher == null
                                || !b.course.teacher.id.equals(tail.course.teacher.id)
                                || b.course.studentGroup == null
                                || b.course.studentGroup.id.equals(groupId) || b.pinned) {
                            continue;
                        }
                        for (Lesson m : byGroup.getOrDefault(b.course.studentGroup.id, List.of())) {
                            if (list.size() >= max) {
                                break;
                            }
                            if (m.id.equals(b.id) || m.pinned || m.timeslot == null
                                    || m.timeslot.getDate().equals(b.timeslot.getDate())
                                    || !b.isSlotLegalFresh(m.timeslot)
                                    || !m.isSlotLegalFresh(b.timeslot)) {
                                continue;
                            }
                            // carried rooms that collide at the swap's landings
                            // get ONE deterministic fresh room each (first free
                            // by id): the full-constraint oracle still refuses
                            // anything the heuristic pick gets wrong
                            Room rb = freshRoomIfCarriedCollides(b, m.timeslot, m,
                                    holdersBySlotRoom, sortedRoomsByLesson);
                            Room rm = freshRoomIfCarriedCollides(m, b.timeslot, b,
                                    holdersBySlotRoom, sortedRoomsByLesson);
                            // review F1: the TAIL's landing room was never
                            // probed - the exact correction the cieco-v3 chain
                            // needed (LAB. TERMOID vs R04). Nobody vacates
                            // the hole: it is empty by definition.
                            Room rt = freshRoomIfCarriedCollides(tail, hole, null,
                                    holdersBySlotRoom, sortedRoomsByLesson);
                            if (rb == NO_ROOM || rm == NO_ROOM || rt == NO_ROOM) {
                                continue; // colliding carried room, no alternative
                            }
                            list.add(Candidate.unblock(tail, hole, b, m, rb, rm, rt));
                        }
                    }
                }
            }
        }
        return list;
    }

    /**
     * Carried room first (the keep side of the cross product; the element may
     * be null: "change nothing on this side", the legal keep-null arm for an
     * H29-style unassigned lesson), then the free ones.
     */
    private static List<Room> withCarried(Room carried, List<Room> free) {
        List<Room> out = new ArrayList<>(free.size() + 1);
        out.add(carried);
        out.addAll(free);
        return out;
    }

    private static boolean sameRoom(Room a, Room b) {
        return a == b || (a != null && b != null && a.id.equals(b.id));
    }

    /**
     * Rooms of the lesson's value range effectively free at the destination:
     * no lesson holds them there, or only {@code vacating} (the partner about
     * to leave that very slot). A second holder keeping the room busy is seen
     * by the oracle, not here. The carried room is skipped: keeping it is the
     * plain candidate.
     */
    private static List<Room> freeRoomsAt(Lesson lesson, Timeslot dest,
            Map<Long, Set<Long>> occupiedRoomsBySlot, Room vacating,
            Map<Lesson, List<Room>> sortedRoomsByLesson) {
        Set<Long> occupied = occupiedRoomsBySlot.getOrDefault(dest.id, Set.of());
        List<Room> out = new ArrayList<>();
        for (Room r : sortedRoomsByLesson.computeIfAbsent(lesson, l -> {
            List<Room> sorted = new ArrayList<>(l.getAvailableRooms());
            sorted.sort(Comparator.comparingLong(room -> room.id));
            return sorted;
        })) {
            if (lesson.room != null && r.id.equals(lesson.room.id)) {
                continue;
            }
            boolean heldByOthers = occupied.contains(r.id)
                    && (vacating == null || !r.id.equals(vacating.id));
            if (!heldByOthers) {
                out.add(r);
            }
        }
        return out;
    }

    private static List<Candidate> generateCandidates(SchoolSchedule problem,
            List<Lesson> targets, int max, Map<Long, List<Lesson>> byGroup) {
        List<Candidate> list = new ArrayList<>();
        var timeslots = problem.getTimeslots();
        // VRP gate (catalog-sweep state 5): a value outside the entity's value
        // range is outside the MODEL (a Stage lesson landed on a non-stage date
        // and the score-time range assertion failed). The engine never proposes
        // such values; neither may the finisher. isSlotLegalFresh is the
        // sanctioned single-slot guard for cold deterministic passes (TASK-578):
        // fresh semantics, no cache, own-slot grandfather included.
        // Round-robin: one CHANGE candidate per target per timeslot-cycle, and
        // one SWAP per target per partner-cycle, so no single family's mass
        // of targets consumes the entire budget before other families get any.
        // The loop runs until BOTH indices exhaust their lists (or the cap): a
        // single empty round must NOT end it (code-review F1: with the VRP gate
        // a whole round can legally add nothing - e.g. tsIdx on a slot every
        // target's calendar closes - and an 'added' flag would truncate the
        // space while the caller declares it exhausted).
        int tsIdx = 0;
        int partnerIdx = 0;
        int maxPartners = 0;
        for (Lesson target : targets) {
            if (target.course == null || target.course.studentGroup == null) {
                continue; // the inner loop guards the same fields (code review F3)
            }
            int n = byGroup.getOrDefault(target.course.studentGroup.id, List.of()).size();
            maxPartners = Math.max(maxPartners, n);
        }
        while (list.size() < max
                && (tsIdx < timeslots.size() || partnerIdx < maxPartners)) {
            for (Lesson target : targets) {
                if (list.size() >= max) break;
                if (target.pinned || target.course == null
                        || target.course.studentGroup == null) {
                    continue;
                }
                // CHANGE: one timeslot per round (cycling through), VRP-legal only
                if (tsIdx < timeslots.size()) {
                    Timeslot ts = timeslots.get(tsIdx);
                    if ((target.timeslot == null || ts.id != target.timeslot.id)
                            && target.isSlotLegalFresh(ts)) {
                        list.add(new Candidate(target, ts, null));
                    }
                }
                // SWAP: one partner per round (cycling through same-group), legal for
                // BOTH sides' value ranges. Same-date partners included: the H28 cure
                // on the 637-on state is an INTRA-DAY swap (the arbitrated
                // Lavorazioni@9 <-> TecnicheSaldatura@12, blind-and-moves REPORT;
                // the originally proposed T10@8 <-> T15TS@10 collides
                // with T15's G08@8 and nets zero); a cross-date-only arm
                // cannot even propose intra-day cures. Any worsening is seen by
                // the full-constraint evaluation either way.
                if (target.timeslot != null) {
                    List<Lesson> partners = byGroup.getOrDefault(
                            target.course.studentGroup.id, List.of());
                    if (partnerIdx < partners.size()) {
                        Lesson partner = partners.get(partnerIdx);
                        if (partner.id != target.id && !partner.pinned
                                && partner.timeslot != null
                                && partner.timeslot.id != target.timeslot.id
                                && target.isSlotLegalFresh(partner.timeslot)
                                && partner.isSlotLegalFresh(target.timeslot)) {
                            list.add(new Candidate(target, null, partner));
                        }
                    }
                }
            }
            tsIdx++;
            partnerIdx++;
        }
        return list;
    }

    // ── Evaluation (full constraint set, incremental via executeTemporaryMove) ─

    // ── Move factory (preview Moves API on the variable metamodel from the
    //    solution descriptor: the proper incremental path; see finish() for why
    //    the metamodel is resolved once) ──────────────────────────────────────

    /**
     * The sanctioned-route downcast from VariableMetaModel (rc-1 exposes the
     * typed accessor only via navigation; a list variable would fail the cast
     * loudly, which we want), shared by every variable this finisher moves.
     */
    @SuppressWarnings("unchecked")
    private static <V> ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel<SchoolSchedule, Lesson, V> variableMetaModel(
            ai.timefold.solver.core.impl.score.director.InnerScoreDirector<SchoolSchedule, BendableScore> director,
            String name, Class<V> type) {
        return (ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel<SchoolSchedule, Lesson, V>)
                director.getSolutionDescriptor().getMetaModel()
                        .entity(Lesson.class)
                        .variable(name, type);
    }

    private static ai.timefold.solver.core.preview.api.move.Move<SchoolSchedule> moveFor(
            ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot> metaModel,
            ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel<SchoolSchedule, Lesson, Room> roomMetaModel,
            Candidate cand) {
        if (cand.isUnblock()) {
            // TASK-657: the blocker swap first (frees the teacher), then the
            // tail slide into the hole; rooms that would collide at the swap's
            // landings are reassigned in the same composite (roomForLesson =
            // blocker's fresh room, roomForPartner = partner's). One move,
            // evaluated/applied whole.
            List<ai.timefold.solver.core.preview.api.move.Move<SchoolSchedule>> parts = new ArrayList<>();
            parts.add(ai.timefold.solver.core.preview.api.move.builtin.Moves.swap(
                    metaModel, cand.blocker, cand.blockerPartner));
            parts.add(ai.timefold.solver.core.preview.api.move.builtin.Moves.change(
                    metaModel, cand.lesson, cand.newTimeslot));
            if (cand.roomForLesson != null) {
                parts.add(ai.timefold.solver.core.preview.api.move.builtin.Moves.change(
                        roomMetaModel, cand.blocker, cand.roomForLesson));
            }
            if (cand.roomForPartner != null) {
                parts.add(ai.timefold.solver.core.preview.api.move.builtin.Moves.change(
                        roomMetaModel, cand.blockerPartner, cand.roomForPartner));
            }
            if (cand.roomForTail != null) {
                parts.add(ai.timefold.solver.core.preview.api.move.builtin.Moves.change(
                        roomMetaModel, cand.lesson, cand.roomForTail));
            }
            return ai.timefold.solver.core.preview.api.move.builtin.Moves.compose(parts);
        }
        ai.timefold.solver.core.preview.api.move.Move<SchoolSchedule> base = cand.isSwap()
                ? ai.timefold.solver.core.preview.api.move.builtin.Moves.swap(metaModel, cand.lesson, cand.swapPartner)
                : ai.timefold.solver.core.preview.api.move.builtin.Moves.change(metaModel, cand.lesson, cand.newTimeslot);
        if (cand.roomForLesson == null && cand.roomForPartner == null) {
            return base;
        }
        List<ai.timefold.solver.core.preview.api.move.Move<SchoolSchedule>> parts = new ArrayList<>();
        parts.add(base);
        if (cand.roomForLesson != null) {
            parts.add(ai.timefold.solver.core.preview.api.move.builtin.Moves.change(
                    roomMetaModel, cand.lesson, cand.roomForLesson));
        }
        if (cand.isSwap() && cand.roomForPartner != null) {
            parts.add(ai.timefold.solver.core.preview.api.move.builtin.Moves.change(
                    roomMetaModel, cand.swapPartner, cand.roomForPartner));
        }
        return ai.timefold.solver.core.preview.api.move.builtin.Moves.compose(parts);
    }

    /**
     * Hard-levels delta of one candidate against the iteration baseline. A
     * throw mid-move is FATAL, not a score: rc-1's executeTemporary has no
     * try/finally, so a partially-applied move leaves the working solution
     * mutated; swallowing it would keep evaluating (and possibly deliver) on a
     * corrupted grid (code-review F3, PASSO 7).
     */
    private static ScoreCompat.HardLevels evaluate(
            ai.timefold.solver.core.impl.score.director.InnerScoreDirector<SchoolSchedule, BendableScore> director,
            ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot> metaModel,
            ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel<SchoolSchedule, Lesson, Room> roomMetaModel,
            Candidate cand, ScoreCompat.HardLevels baseline) {
        try {
            var after = ScoreCompat.hardLevels((BendableScore) director
                    .executeTemporaryMove(moveFor(metaModel, roomMetaModel, cand), false).raw());
            return new ScoreCompat.HardLevels(after.pure() - baseline.pure(),
                    after.quasi() - baseline.quasi());
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "ViolationScopedSteepestFinisher: candidate evaluation threw; the working"
                            + " solution state is not guaranteed, aborting the walk", e);
        }
    }

    private static void apply(
            ai.timefold.solver.core.impl.score.director.InnerScoreDirector<SchoolSchedule, BendableScore> director,
            ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel<SchoolSchedule, Lesson, Timeslot> metaModel,
            ai.timefold.solver.core.preview.api.domain.metamodel.PlanningVariableMetaModel<SchoolSchedule, Lesson, Room> roomMetaModel,
            Candidate best) {
        director.executeMove(moveFor(metaModel, roomMetaModel, best));
        String rooms = best.roomForLesson == null && best.roomForPartner == null ? ""
                : String.format(" +ROOM %s/%s",
                        best.roomForLesson == null ? "-" : best.roomForLesson.name,
                        best.roomForPartner == null ? "-" : best.roomForPartner.name);
        if (best.isUnblock()) {
            LOG.infof("  applied UNBLOCK chain: [%s <-> %s in %s] + [%s -> %s] (delta=%s)",
                    best.blocker.course.subject, best.blockerPartner.course.subject,
                    best.blocker.course.studentGroup.name,
                    best.lesson.course.subject, best.newTimeslot.getDate(), best.delta);
        } else if (best.isSwap()) {
            LOG.infof("  applied SWAP%s: %s <-> %s (delta=%s)",
                    rooms, best.lesson.course.subject, best.swapPartner.course.subject, best.delta);
        } else {
            LOG.infof("  applied CHANGE%s: %s -> %s (delta=%s)",
                    rooms, best.lesson.course.subject, best.newTimeslot.getDate(), best.delta);
        }
    }
}
