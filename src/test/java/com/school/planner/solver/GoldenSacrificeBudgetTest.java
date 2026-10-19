package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.school.planner.solver.GoldenSacrificeBudget.GoldenLesson;
import com.school.planner.solver.GoldenSacrificeBudget.QuotaDay;

/**
 * TASK-537: golden-derived quasi-hard budgets. The real-seed test pins the
 * audit numbers of 20/08 (with the divergences documented in the class
 * javadoc); the synthetic test pins the exclusion protocol (Saturday, Stage
 * subject, Fasullo teacher) and the counting logic.
 */
class GoldenSacrificeBudgetTest {

    private static final LocalDate MON = LocalDate.of(2026, 9, 14);
    private static final LocalDate TUE = LocalDate.of(2026, 9, 15);
    private static final LocalDate WED = LocalDate.of(2026, 9, 16);

    @Test
    void realSeedBudgetsMatchAudit() {
        GoldenSacrificeBudget b = GoldenSacrificeBudget.fromClasspathSeed();
        // Audit 20/08: H33 4 days (G09 x2, G08 x2), H11 43 days over 6h
        // under the practiced cap 8, H31 total 1 non-contiguous day (G10).
        assertEquals(4, b.h33BudgetDays(), "H33 golden under-floor days");
        assertEquals(43, b.h11DaysOverPracticedCap(), "H11 golden days over 6h");
        assertEquals(1, b.h31BudgetDaysPerGroup().values().stream().mapToInt(Integer::intValue).sum(),
                "H31 golden non-contiguous days");
        assertEquals(1, b.h31BudgetDaysPerGroup().get("G10"), "H31 is one G10 day");
        // DIVERGENCE from the audit (documented in GoldenSacrificeBudget's
        // javadoc): the faithful H32 population (isQuotaFillExcluded scope +
        // the G09 1h tolerance, the same charge semantics as
        // FinalClosingCampaign.findDonor) yields 0 hours, because the golden's
        // only in-scope under-quota day (G09 2026-10-01, 5h vs quota 6) is
        // absorbed by the tolerance. The audit's 1h counts that same day
        // WITHOUT the tolerance: the number is reproduced below, on the record,
        // rather than silently absorbed into the budget.
        assertEquals(0, b.h32BudgetHours(), "H32 with the G09 tolerance (audit said 1h raw)");
    }

    @Test
    void syntheticFixtureExclusionProtocolAndCounting() {
        // Group X (in H32 scope): Mon 3h under quota 5 -> H32 deficit 2, and
        // under floor 4 -> H33 day; Tue 7h -> H11 day over 6; Wed 5h at quota,
        // at floor. Pool 15h over 3 session days -> math floor 5 -> floor 4.
        List<GoldenLesson> golden = List.of(
                // Monday: 3 placed hours (slots 0,1,2), one Saturday twin ignored
                lesson(MON, 0, "X", "Rossi", "Italiano"),
                lesson(MON, 1, "X", "Rossi", "Italiano"),
                lesson(MON, 2, "X", "Bianchi", "Storia"),
                lesson(MON.plusDays(5), 3, "X", "Rossi", "Italiano"), // Saturday: excluded
                // Tuesday: 7 placed hours -> H11 day; slot 5 is Stage, teacher
                // slot 6 is Fasullo: neither counts, so the day is exactly 7
                lesson(TUE, 0, "X", "Bianchi", "Storia"),
                lesson(TUE, 1, "X", "Bianchi", "Storia"),
                lesson(TUE, 2, "X", "Bianchi", "Storia"),
                lesson(TUE, 3, "X", "Bianchi", "Storia"),
                lesson(TUE, 4, "X", "Bianchi", "Storia"),
                lesson(TUE, 5, "X", "Bianchi", "Storia"),
                lesson(TUE, 6, "X", "Bianchi", "Storia"),
                lesson(TUE, 7, "X", "Stage", "Stage"), // Stage subject: excluded
                lesson(TUE, 0, "X", "Fasullo", "Mate"), // Fasullo: excluded
                // Wednesday: 4 hours at slots 0,2,3,4 -> at floor (4), not
                // contiguous (hole at 1) -> one H31 day, no H33 charge
                lesson(WED, 0, "X", "Rossi", "Italiano"),
                lesson(WED, 2, "X", "Rossi", "Italiano"),
                lesson(WED, 3, "X", "Rossi", "Italiano"),
                lesson(WED, 4, "X", "Rossi", "Italiano"));
        List<QuotaDay> quotas = List.of(
                new QuotaDay("X", MON, 5),
                new QuotaDay("X", TUE, 7),
                new QuotaDay("X", WED, 4));
        GoldenSacrificeBudget b = GoldenSacrificeBudget.derive(golden, quotas);
        // H32: Monday 3h vs quota 5 (no tolerance for X) -> deficit 2
        assertEquals(2, b.h32BudgetHours());
        // H33: pool 3+7+4 = 14 placed hours over 3 session days -> math floor
        // 4 -> floor 4; Monday (3h) is the only day under it
        assertEquals(1, b.h33BudgetDays());
        assertEquals(1, b.h11DaysOverPracticedCap(), "Tuesday is exactly 7 counted hours");
        assertEquals(1, b.h31BudgetDaysPerGroup().get("X"), "Wednesday slots 0,2 have a hole at 1");
        assertTrue(b.h31BudgetDaysPerGroup().containsKey("X"));
    }

    @Test
    void syntheticExcludedGroupDoesNotPayH32() {
        // G04 is out of H32 scope (isQuotaFillExcluded): its under-quota day
        // must not be counted, but the H33/H11 axes still see it
        List<GoldenLesson> golden = List.of(
                lesson(MON, 0, "G04", "Verdi", "Sicurezza"),
                lesson(MON, 1, "G04", "Verdi", "Sicurezza"));
        List<QuotaDay> quotas = List.of(new QuotaDay("G04", MON, 5), new QuotaDay("G04", TUE, 5));
        GoldenSacrificeBudget b = GoldenSacrificeBudget.derive(golden, quotas);
        assertEquals(0, b.h32BudgetHours(), "excluded group pays no H32");
        // (the tiny pool collapses the H33 floor to 1h here, so this fixture
        // asserts only the H32 scope; the floor logic is covered above)
    }

    private static GoldenLesson lesson(LocalDate date, int slot, String group, String teacher, String subject) {
        return new GoldenLesson(date, slot, group, teacher, subject);
    }
}
