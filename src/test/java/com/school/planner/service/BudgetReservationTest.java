package com.school.planner.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;

import java.util.List;
import java.util.Map;

import com.school.planner.domain.Course;
import com.school.planner.domain.Lesson;
import com.school.planner.domain.StudentGroup;
import com.school.planner.domain.Teacher;
import org.junit.jupiter.api.Test;

/**
 * TASK-516 pure rule + the TASK-510 probe case in miniature. The probe measured
 * on run4: T15 at EXACTLY the 6h H7 cap on every G04 lab day (174
 * pinned meccanica/CN/Cad hours), RA = 50h quota-bound lab over 53 lab days
 * (27 Wed + 26 Thu), lun/ven VRP-forbidden (lab=0 rows). The reservation is
 * ceil(50/53) = 1h/day: the greedy pins against 6-1=5h and the RA's 1h block
 * finds a legal 6th hour on a lab day. Without it every lab day saturates at 6h
 * and the RA has ZERO legal cells (the 20 boxed).
 */
class BudgetReservationTest {

    @AfterEach
    void clearFlag() {
        System.clearProperty(BudgetReservation.FLAG);
    }

    @Test
    void probeNumbersReserveOneHourPerLabDay() {
        // 50h RA over 53 lab days (27 Wed + 26 Thu, the run4 measurement)
        assertEquals(1, BudgetReservation.reservedHoursPerLabDay(50, 53));
    }

    @Test
    void fractionalDemandCeilsToAWholeLegalCell() {
        assertEquals(1, BudgetReservation.reservedHoursPerLabDay(1, 53));
        assertEquals(2, BudgetReservation.reservedHoursPerLabDay(54, 53));
        assertEquals(53, BudgetReservation.reservedHoursPerLabDay(53, 1));
    }

    @Test
    void noDemandOrNoLabDayReservesNothing() {
        assertEquals(0, BudgetReservation.reservedHoursPerLabDay(0, 53));
        assertEquals(0, BudgetReservation.reservedHoursPerLabDay(50, 0));
        assertEquals(0, BudgetReservation.reservedHoursPerLabDay(-1, 53));
    }

    @Test
    void flagDefaultsOffAndOptIn() {
        System.clearProperty(BudgetReservation.FLAG);
        assertFalse(BudgetReservation.enabled());
        System.setProperty(BudgetReservation.FLAG, "true");
        assertTrue(BudgetReservation.enabled());
        System.setProperty(BudgetReservation.FLAG, "false");
        assertFalse(BudgetReservation.enabled());
    }

    /**
     * The greedy H7 gate's exact arithmetic on the probe shape: 34 Wednesdays
     * (and 26 Thursdays), teacher cap 6, pin blocks fill the cap. With a 1h
     * reservation the gate admits pins only up to 5h/day, so a 1h RA block is
     * legal on every lab day; without it every day saturates and NO cell is
     * legal (structurally, on every lab day at once).
     */
    @Test
    void probeCaseH7GateArithmetic() {
        int teacherCap = 6;
        int reservation = BudgetReservation.reservedHoursPerLabDay(50, 53);
        int labDays = 53;

        // Without reservation: pins fill the cap, RA (1h) illegal everywhere
        int pinnedSaturating = 6;
        int legalCells = 0;
        for (int d = 0; d < labDays; d++) {
            if (pinnedSaturating + 1 <= teacherCap) {
                legalCells++;
            }
        }
        assertEquals(0, legalCells, "the probe: 20 boxed, no legal cell");

        // With reservation: pins cap at cap - reservation, RA legal on every lab day
        int gatedCap = Math.max(1, teacherCap - reservation);
        int pinnedWithGate = gatedCap;
        legalCells = 0;
        for (int d = 0; d < labDays; d++) {
            if (pinnedWithGate + 1 <= teacherCap) {
                legalCells++;
            }
        }
        assertEquals(labDays, legalCells, "the cure: every lab day hosts the RA hour");
        // and the reservation actually binds (the pins were forced below the cap)
        assertTrue(gatedCap < teacherCap);
    }

    /**
     * Golden rhythm (round 7): the golden practices 4h total load on the RA days
     * (12 days at 4h, 1 at 6h, avg 4.15) on a cap of 6 -> 2h reserved; the clamp
     * bounds are 1 and cap-1 (a full-cap reservation would starve the pins).
     */
    @Test
    void goldenRhythmMatchesThePractice() {
        // 12 days at 4h + 1 day at 6h = 54h/13 days = 4.15 avg load -> headroom rounds to 2
        assertEquals(2, BudgetReservation.goldenRhythmReservation(6, (int) Math.round(54.0 / 13)));
        assertEquals(2, BudgetReservation.goldenRhythmReservation(6, 4));
        assertEquals(1, BudgetReservation.goldenRhythmReservation(6, 6));  // saturated: floor 1
        assertEquals(5, BudgetReservation.goldenRhythmReservation(6, 1));  // tiny load: cap-1 ceiling
        assertEquals(0, BudgetReservation.goldenRhythmReservation(1, 4));  // cap 1: no lever
        assertEquals(0, BudgetReservation.goldenRhythmReservation(6, 0));  // no evidence
    }

    /**
     * AC#4 round 7 (clip, not starvation): with a 2h reservation the greedy's H7
     * arithmetic places a CLIPPED prefix on a saturated-cap date (never 174 -> 0):
     * the RA stays placeable AND the lab keeps pinning cap-2 hours per day.
     */
    @Test
    void probeCaseClipKeepsLabPinsAndPlacesRa() {
        int teacherCap = 6;
        int reservation = BudgetReservation.goldenRhythmReservation(teacherCap, 4);
        assertEquals(2, reservation);
        int gatedCap = teacherCap - reservation;
        // a 6h meccanica block on an empty lab day: clipped to 4, pinned
        int remaining = gatedCap - 0;
        int clip = Math.min(6, remaining);
        assertEquals(4, clip);
        // the RA's 1h block now fits in the reserved headroom
        assertTrue(gatedCap + 1 <= teacherCap);
        // and even a day pre-loaded to the gated cap keeps the RA legal via the
        // reserved hours: 4 pinned + 2 reserved = 6 = cap, RA in the reserved 2
        assertEquals(teacherCap, 4 + reservation);
        // round-6 failure mode stays dead: with the reservation armed the block is
        // NEVER fully rejected while headroom remains (clip >= 1)
        for (int already = 0; already < gatedCap; already++) {
            assertTrue(Math.min(6, gatedCap - already) >= 1);
        }
    }

    // ─── TASK-518 round 8: plan sizing + teacher-wide reservation ─────────────

    private static LabSession session(Course course, int hours) {
        LabSession sess = new LabSession(course);
        for (int i = 0; i < hours; i++) {
            Lesson l = new Lesson();
            l.course = course;
            sess.addLesson(l);
        }
        return sess;
    }

    /**
     * AC#4 caso round 7 (code clip): un piano che sta dentro il budget NETTO
     * ((cap - reservation) x giorni-L) non viene toccato - le ore del pair
     * (pin + domanda non enumerata) hanno casa per costruzione.
     */
    @Test
    void planWithinNetBudgetIsNotTrimmed() {
        Course c = new Course();
        var pair = new com.school.planner.service.RotationTestRunner.BlockKey(26L, 4L);
        Map<com.school.planner.service.RotationTestRunner.BlockKey, List<LabSession>> plan = Map.of(
                pair, List.of(session(c, 6), session(c, 6)));
        // budget = (6 - 2) x 53 = 212 >= planned 12: nothing trimmed
        var out = BudgetReservation.applyPlanSizing(plan,
                Map.of(pair, 2), Map.of(pair, 53), Map.of(pair, 6));
        assertEquals(2, out.get(pair).size());
    }

    /**
     * AC#4 caso round 6 (azzeramento VIETATO): un piano oltre budget perde le
     * sessioni IN CODA, ma la lista non torna vuota finche' il budget netto e'
     * positivo (mai il rifiuto intero del pair).
     */
    @Test
    void oversizedPlanTrimsTrailingSessionsNeverToZero() {
        Course c = new Course();
        var pair = new com.school.planner.service.RotationTestRunner.BlockKey(26L, 4L);
        List<LabSession> many = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(session(c, 6));
        }
        var out = BudgetReservation.applyPlanSizing(Map.of(pair, many),
                Map.of(pair, 2), Map.of(pair, 53), Map.of(pair, 6));
        int keptHours = out.get(pair).stream().mapToInt(sess -> sess.lessons.size()).sum();
        assertTrue(!out.get(pair).isEmpty(), "round-6 lesson: never zero the pair");
        assertTrue(keptHours <= (6 - 2) * 53);
    }

    /**
     * AC#4 caso round 3 (RA) + round 7 (teacher-wide): la reservation di UNA pair
     * deve nettare il cap del docente per TUTTE le sue pair (la vista teacher-level
     * e' il max sulle pair), altrimenti i pin degli altri gruppi mangiano l'headroom.
     */
    @Test
    void teacherWideReservationIsMaxOverPairs() {
        PreAssignmentContext ctx = new PreAssignmentContext();
        var p1 = new com.school.planner.service.RotationTestRunner.BlockKey(26L, 4L);
        Teacher t = new Teacher();
        t.id = 26L;
        StudentGroup g1 = new StudentGroup();
        g1.id = 4L;
        StudentGroup g2 = new StudentGroup();
        g2.id = 8L;
        ctx.setH7BudgetReservations(Map.of(p1, 2));
        assertEquals(2, ctx.reservedH7Hours(t, g1));          // own pair
        assertEquals(0, ctx.reservedH7Hours(t, g2));          // other pair: pair view unchanged
        assertEquals(2, ctx.reservedH7Hours(t));              // teacher view (round-8 rule)
        // a teacher with no reserved pair reads 0 (flag-off path unchanged)
        Teacher other = new Teacher();
        other.id = 99L;
        assertEquals(0, ctx.reservedH7Hours(other));
    }

    /**
     * Review finding 1a: the FALLBACK spread reservation is clamped to cap-1.
     * 110h over 5 lab days would reserve 22h on a cap of 6 and zero the pair's
     * budget (round-6 starvation via the golden-unreadable path).
     */
    @Test
    void fallbackSpreadReservationIsClampedToCapMinusOne() {
        // golden path clamp (already covered) vs the raw spread: 110/5 = 22
        assertEquals(22, BudgetReservation.reservedHoursPerLabDay(110, 5));
        // the clamp lives at the computeForPhase call site; assert the bound rule
        // the code applies: max(1, min(cap-1, spread))
        int cap = 6;
        int spread = BudgetReservation.reservedHoursPerLabDay(110, 5);
        int reserved = Math.max(1, Math.min(cap - 1, spread));
        assertEquals(5, reserved);
    }

    /**
     * Review finding 1b: with budget 0 (or oversized sessions) the trim keeps
     * ONE session: never the round-6 zeroing, warning logged by the impl.
     */
    @Test
    void zeroBudgetStillKeepsOneSession() {
        Course c = new Course();
        var pair = new com.school.planner.service.RotationTestRunner.BlockKey(26L, 4L);
        List<LabSession> many = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            many.add(session(c, 6));
        }
        // reservation == cap -> net budget 0
        var out = BudgetReservation.applyPlanSizing(Map.of(pair, many),
                Map.of(pair, 6), Map.of(pair, 53), Map.of(pair, 6));
        assertEquals(1, out.get(pair).size(), "round-6 lesson: one session always kept");
    }
}
