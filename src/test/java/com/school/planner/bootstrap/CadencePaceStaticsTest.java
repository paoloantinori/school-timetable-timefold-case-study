package com.school.planner.bootstrap;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.school.planner.domain.SchoolSchedule;
import com.school.planner.solver.TimeslotSequence;
import com.school.planner.util.SubjectNormalization;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TASK-636: the test-side cadence publication (SnapshotFixture) must agree
 * with what the tail consumes (SchoolSchedule.getCadenceGoldenCumulative).
 * Pinned against the real reference: G06 / Servizio tecnico e stilistico is
 * the golden's concentrated pair (6h/week W39-43, gap, 6h/week W04-08), so by
 * end of October the golden cumulative is 30 and by mid-winter it stays 30
 * until the spring burst closes it at 60.
 */
class CadencePaceStaticsTest {

    private static int cum(String group, String subject, LocalDate date) {
        return SchoolSchedule.getCadenceGoldenCumulative(
                group, SubjectNormalization.normalize(subject),
                TimeslotSequence.isoWeekKey(date));
    }

    @Test
    void publishedPaceCurveMatchesTheReferenceSeries() {
        SnapshotFixture.publishCadenceReferenceStatics();
        assertTrue(SchoolSchedule.cadencePaceCurveCount() > 0,
                "reference published no pace curves - publication is inert");
        String group = "G06";
        String subject = "Servizio tecnico e stilistico";
        // W39 ends 2026-09-27: 6h. W43 ends 2026-10-25: 30h (autumn burst done).
        assertEquals(6, cum(group, subject, LocalDate.of(2026, 9, 25)));
        assertEquals(30, cum(group, subject, LocalDate.of(2026, 10, 30)));
        // Inside the gap the curve plateaus at 30 (never behind during the gap).
        assertEquals(30, cum(group, subject, LocalDate.of(2026, 12, 15)));
        assertEquals(30, cum(group, subject, LocalDate.of(2027, 1, 20)));
        // Spring burst closes the curve at the pair total.
        assertEquals(60, cum(group, subject, LocalDate.of(2027, 2, 26)));
        // After the window: exhausted (total), before the window: 0.
        assertEquals(60, cum(group, subject, LocalDate.of(2027, 5, 15)));
        assertEquals(0, cum(group, subject, LocalDate.of(2026, 9, 15)));
        // A pair the reference does not cover is inert (MAX sentinel).
        assertEquals(Integer.MAX_VALUE, cum("GRUPPO INESISTENTE", "Materia", LocalDate.of(2026, 10, 30)));
    }
}
