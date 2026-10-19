package com.school.planner.solver;

import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;

import ai.timefold.solver.core.impl.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;

/**
 * TASK-499 (Enterprise reimplementation #2, design doc {@code
 * claudedocs/design/nearby-selection-community.md}): a COMMUNITY nearby selection for
 * the timeslot change moves - a TEMPLATE-PROXIMITY FILTER on the move selector, the
 * local analogue of OptaPlanner 8.x's nearby selection (which the Timefold fork moved
 * to Enterprise as a distance-ordered, parabolically biased value selector).
 *
 * <p><b>Why.</b> The change move selector generates a move per (lesson, timeslot) pair:
 * 1368 candidate timeslots per lesson, most of them OFF the course's template. M32
 * penalizes those moves AFTER the forager has paid their evaluation cost; this filter
 * prevents the grade-3 moves from being GENERATED, so the same LS wall buys more
 * useful evaluations. It composes with M32 (same accept/reject outcome, one
 * evaluation earlier) rather than replacing it.
 *
 * <p><b>Proximity grades</b> (the "distance meter" is the template itself):
 * <ol>
 * <li>grade 0: the destination is one of the course's template cells - the M32-neutral
 *     placement ({@link SchoolSchedule#isOnTemplateCell}, the SAME single source M32
 *     reads, so the two can never disagree about what a template cell is);</li>
 * <li>grade 1: same weekday as some template cell, different slot (the right day, a
 *     different hour);</li>
 * <li>grade 2: same slot as some template cell, different weekday (the cadence column
 *     M32 already treats as non-deviation);</li>
 * <li>grade 3: everything else. THOSE are filtered, with keep-probability {@code p}:
 *     a fraction of far moves still passes, because the bad basins the census
 *     documented are sometimes escaped only by a far jump.</li>
 * </ol>
 *
 * <p><b>Honest disclaimer vs the Enterprise feature.</b> The Enterprise nearby
 * selection ORDERS values by distance and samples them with a biased distribution:
 * every value stays reachable. This filter is binary (accept/reject): the reachability
 * guarantee is the keep-probability on grade 3, not an ordering. Same intent
 * (concentrate the search near the template), coarser mechanism, community-only API.
 *
 * <p><b>Wiring.</b> Staged path only (like the MT-LS wiring, see
 * {@code ScheduleService.runStagedSolver}): the flag arms the filter on the timeslot
 * change move selectors of the LS phases. Default OFF, opt-in prototype behind
 * maintainer's gate: {@code -Dplanner.ls.nearby-template-filter.enabled=true}, keep
 * probability knob {@code planner.ls.nearby-template-filter.keep-probability}
 * (default 0.20). The filter's randomness is {@link ThreadLocalRandom}: it is NOT
 * seeded by the GRASP construction seed, so an armed run is not reproducible move by
 * move (accepted for a wall-measuring prototype; the A/B gate reads wall and KPI, not
 * determinism).
 */
public class NearbyTemplateMoveFilter implements SelectionFilter<SchoolSchedule, Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NearbyTemplateMoveFilter.class);

    /** Fraction of grade-3 (far) moves that still pass: the escape-valve default. */
    public static final double DEFAULT_KEEP_PROBABILITY = 0.20d;

    private final double keepProbability;

    public NearbyTemplateMoveFilter() {
        this.keepProbability = parseKeepProbability();
        LOGGER.info("NearbyTemplateMoveFilter armed: keepProbability={} (grade 3 passes with this probability)",
                keepProbability);
    }

    /**
     * The ONE parse-and-clamp of the keep-probability knob (shared with the banner via
     * {@code ScheduleService.nearbyTemplateKeepProbability}, which delegates here):
     * malformed values (including the Italian-decimal typo {@code 0,2}) and out-of-range
     * values clamp to the default instead of throwing - Timefold instantiates this class
     * reflectively inside {@code buildSolver}, so an unguarded parse would kill the whole
     * staged solve at minute 0.
     */
    public static double parseKeepProbability() {
        double p;
        try {
            p = Double.parseDouble(System.getProperty("planner.ls.nearby-template-filter.keep-probability",
                    String.valueOf(DEFAULT_KEEP_PROBABILITY)));
        } catch (NumberFormatException e) {
            return DEFAULT_KEEP_PROBABILITY;
        }
        // Out-of-range clamps to the default rather than poisoning the search
        // (0 would wall the search inside the template; >1 would disable the filter).
        return (p >= 0.0d && p <= 1.0d) ? p : DEFAULT_KEEP_PROBABILITY;
    }

    /**
     * Accept every move that is NOT a far (grade-3) timeslot change. Swap/pillar moves
     * keep both their timeslots in the same value pool as the entity's own placement,
     * which the grades below judge the same way; non-Lesson selections pass untouched.
     */
    @Override
    public boolean accept(ScoreDirector<SchoolSchedule> scoreDirector, Object selection) {
        if (!(selection instanceof ai.timefold.solver.core.preview.api.move.Move<?> move)) {
            return true;   // not a move: nothing to grade (defensive; wired on move selectors)
        }
        Lesson lesson = lessonOf(move);
        Timeslot to = timeslotOf(move);
        if (lesson == null || to == null) {
            return true;   // not a timeslot move we understand: never block it
        }
        Long courseId = lesson.course != null ? lesson.course.id : null;
        if (courseId == null || to.getDate() == null) {
            return true;
        }
        // M32's own exemption, mirrored: pseudo (Stage) and null-teacher lessons are
        // never penalized for a far cell, so the filter must not block them either -
        // otherwise the "composes with M32" claim is false and the A/B measures a bias
        // M32 deliberately lacks (the seeded stage tail is 2,320 lessons the LS moves).
        var teacher = lesson.getTeacher();
        if (teacher == null || teacher.pseudo) {
            return true;
        }
        int grade = proximityGrade(courseId, to);
        if (grade <= 2) {
            return true;
        }
        return ThreadLocalRandom.current().nextDouble() < keepProbability;
    }

    /**
     * The proximity grade of a destination timeslot relative to the course's template:
     * 0 on a template cell, 1 same-weekday-different-slot, 2 same-slot-different-weekday,
     * 3 far. A course WITHOUT a template reference has no proximity to protect: grade 0
     * (pass everything) - the filter must not become a second, harsher M32 for the
     * untemplated courses.
     */
    static int proximityGrade(Long courseId, Timeslot to) {
        var cells = SchoolSchedule.getWeeklyTemplateCells(courseId);
        if (cells == null || cells.isEmpty()) {
            return 0;   // untemplated course: the filter stands aside
        }
        int slotIndex = TimeslotSequence.slotIndexOf(to);
        if (slotIndex < 0) {
            return 0;   // no canonical slot: cannot grade, do not block
        }
        if (SchoolSchedule.isOnTemplateCell(courseId, to)) {
            return 0;
        }
        var weekday = to.getDate().getDayOfWeek();
        boolean sameWeekday = false;
        boolean sameSlot = false;
        // encodeTemplateCell = dayOfWeek.getValue() * SLOTS_PER_DAY + slotIndex, the M32
        // single source: decoding inline (rather than adding decode helpers to the
        // domain) keeps this prototype's surface to one new class.
        for (Integer encoded : cells) {
            if (encoded / TimeslotSequence.SLOTS_PER_DAY == weekday.getValue()) {
                sameWeekday = true;
            }
            if (encoded % TimeslotSequence.SLOTS_PER_DAY == slotIndex) {
                sameSlot = true;
            }
        }
        if (sameWeekday || sameSlot) {
            return sameWeekday ? 1 : 2;
        }
        return 3;
    }

    private static Lesson lessonOf(ai.timefold.solver.core.preview.api.move.Move<?> move) {
        for (Object entity : move.getPlanningEntities()) {
            if (entity instanceof Lesson lesson) {
                return lesson;
            }
        }
        return null;
    }

    private static Timeslot timeslotOf(ai.timefold.solver.core.preview.api.move.Move<?> move) {
        for (Object value : move.getPlanningValues()) {
            if (value instanceof Timeslot timeslot) {
                return timeslot;
            }
        }
        return null;
    }
}
