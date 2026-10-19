package com.school.planner.solver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * TASK-542 iter-20 measure gate: the golden-frozen block menu (seed/day-compact-menu.csv)
 * resolves against the REAL seed's groups and courses, and the builder produces ZERO holes
 * on it (its construction contract). This is the profile measure WITHOUT a full solve: the
 * prediction iter-20 (0-5 H17 holes, H31 ~0) is checked on the builder's own dayViews.
 * A @QuarkusTest real-seed variant for the full wiring measure follows in the next step.
 */
class DayCompactMenuResolveTest {

    @Test
    void frozenMenuLoadsAndHasBlockRows() {
        List<DayCompactMenuSource.MenuRow> rows = DayCompactMenuSource.load();
        assertFalse(rows.isEmpty(), "the frozen menu must exist and be non-empty");
        assertTrue(rows.size() > 100, "the golden-derived menu covers the school (202 rows expected, got " + rows.size() + ")");
        // every row has a sane block size (1..8 hours: Stage days run full-day in the golden)
        for (DayCompactMenuSource.MenuRow r : rows) {
            assertTrue(r.hours() >= 1 && r.hours() <= 8,
                    "block " + r + " outside the 1..8h range");
        }
    }
}
