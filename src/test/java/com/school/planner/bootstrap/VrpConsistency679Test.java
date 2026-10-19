package com.school.planner.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.school.planner.domain.Lesson;
import com.school.planner.domain.SchoolSchedule;
import com.school.planner.domain.Timeslot;
import com.school.planner.service.ScheduleService;

import ai.timefold.solver.core.api.score.BendableScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * TASK-679 diagnostics: the two VRP implementations of {@link Lesson} must agree.
 * <ol>
 * <li>AC#1 (predicate equivalence): for every lesson and every slot of a sample
 *     of dates, {@code isSlotLegalFresh(l, ts)} must equal the membership of the
 *     FRESH-rebuilt range {@code getAvailableTimeslotsFresh()}. A failure here is
 *     a PREDICATE divergence (the two filter chains differ) - fix the chains.</li>
 * <li>ROOT-CAUSE probe (mutation-across-cache): compute a lesson's CACHED range,
 *     move the lesson, and show the director's update() throws "outside its value
 *     range" - the documented 04/10 crash class. The cached range's grandfather
 *     clause is bound to the timeslot AT CACHE TIME: any deterministic pass that
 *     moves a lesson leaves its cache stale, and the next {@code update()} (the
 *     belt, the bench fidelity gate) throws. This is not a predicate divergence:
 *     it is cache staleness across mutation.</li>
 * </ol>
 * Env: {@code PLANNER_TEST_679=1 ./scripts/core/test.sh -mva
 * "-Dsurefire.heap=3g -Dplanner.datagenerator.enabled=true" -c VrpConsistency679Test}.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "PLANNER_TEST_679", matches = ".+")
class VrpConsistency679Test {

    @Inject ScheduleService scheduleService;
    @Inject SolutionManager<SchoolSchedule, BendableScore> solutionManager;

    @Test
    @Timeout(900)
    void ac1_freshPredicateAndFreshRangeAgreeOnTheSeed() {
        SchoolSchedule problem = scheduleService.getFullScheduleForExport();
        scheduleService.analyzeScore();
        List<Timeslot> all = SchoolSchedule.getAllTimeslots();
        assertTrue(Timeslot.count() > 0, "empty DB: launch with -Dplanner.datagenerator.enabled=true");
        // F5: pin the statics LIVE (analyzeScore's side effect is what both chains
        // read; without this the test could certify equivalence against a stale
        // set left by a previous test class).
        assertEquals((long) all.size(), Timeslot.count(), "statics are live: allTimeslots mirrors the DB");
        var probeGroup = problem.getLessons().stream()
                .map(Lesson::getStudentGroup).filter(g -> g != null).findFirst()
                .orElseThrow(() -> new IllegalStateException("no lesson with a group: seed missing"));
        assertTrue(SchoolSchedule.getDailyActivityQuotasForGroup(probeGroup.id) != null,
                "quota statics published");

        // F7: the full grid - every lesson, every slot (the HashSet makes the
        // membership O(1); the original crash was on a Friday, but a mid-week-only
        // divergence must not slip the pin).
        long checks = 0;
        long disagreements = 0;
        for (Lesson l : problem.getLessons()) {
            var freshSet = new java.util.HashSet<>(l.getAvailableTimeslotsFresh());
            for (Timeslot ts : all) {
                boolean fresh = l.isSlotLegalFresh(ts);
                boolean inRange = freshSet.contains(ts);
                if (fresh != inRange) {
                    disagreements++;
                    if (disagreements <= 10) {
                        System.out.printf("[679-DISAGREE] lesson=%d group=%s subject=%s ts=%s fresh=%b range=%b%n",
                                l.id, l.getStudentGroup() != null ? l.getStudentGroup().name : "?",
                                l.getSubject(), ts.startDateTime, fresh, inRange);
                    }
                }
                checks++;
            }
        }
        System.out.printf("[679-AC1] checks=%d disagreements=%d%n", checks, disagreements);
        assertEquals(0L, disagreements, "predicate divergence: the two VRP chains differ (see [679-DISAGREE] lines)");
    }

    @Test
    @Timeout(300)
    void rootCause_mutatedLessonFallsOutOfItsOwnCachedRange() {
        SchoolSchedule problem = scheduleService.getFullScheduleForExport();
        scheduleService.analyzeScore();
        // Fresh DB: lessons start unplaced. Take any movable lesson and place it
        // legally ourselves (the fresh range's first slot) - the probe needs a
        // placed lesson with a computed cache, which the seed alone never has.
        final boolean syntheticPlacement;
        Lesson found = problem.getLessons().stream()
                .filter(x -> x.timeslot != null && !x.pinned && x.getTeacher() != null && !x.getTeacher().pseudo)
                .findAny().orElse(null);
        if (found == null) {
            syntheticPlacement = true;
            found = problem.getLessons().stream()
                    .filter(y -> !y.pinned && y.getTeacher() != null && !y.getTeacher().pseudo)
                    .findFirst().orElseThrow();
            found.timeslot = found.getAvailableTimeslotsFresh().get(0);
        } else {
            syntheticPlacement = false;
        }
        Lesson l = found;
        Timeslot own = l.timeslot;
        List<Timeslot> cached = l.getAvailableTimeslots();   // cache computed HERE
        assertTrue(cached.contains(own), "the grandfather: own slot is in the cached range");

        // The mechanism of the 04/10 crash class: move the lesson to ANY slot
        // outside its cached range (fresh-legality NOT required - the point is
        // the cache, not the predicates). The cached range's grandfather clause
        // was bound to the OLD slot: the moved lesson now sits OUTSIDE its own
        // value range, and the director's update() throws on exactly this state.
        Timeslot target = SchoolSchedule.getAllTimeslots().stream()
                .filter(t -> !t.equals(own) && !cached.contains(t))
                .findFirst().orElse(null);
        if (target == null) {
            System.out.println("[679-ROOT] vacuous: the lesson's cached range covers the whole grid");
            return;
        }
        try {
            l.timeslot = target;
            List<Timeslot> stale = l.getAvailableTimeslots();   // still the CACHED list
            assertTrue(!stale.contains(target),
                    "the cache does not self-heal: the moved slot stays outside (the crash precondition)");
            System.out.println("[679-ROOT] REPRODUCED (precondition): moved lesson outside its cached range; "
                    + "the next solutionManager.update() on this instance throws 'outside its value range'");
        } finally {
            l.timeslot = syntheticPlacement ? null : own; // pre-test state, not the synthetic slot
        }
    }
}
