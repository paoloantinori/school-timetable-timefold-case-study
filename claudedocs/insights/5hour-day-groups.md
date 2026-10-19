# 5-Hour Day Groups (spalmone analysis)

> 📌 **Session memory** (~March 2026). Point-in-time finding — verify against current `DataGenerator` and DB before relying on specifics. Note: this memory contains an internal discrepancy (flagged below) and touches `DataGenerator`, which is change-protected.

## Finding

G08 and G13 max out at 5 hours/day (Mon–Thu) in the spalmone human schedule.

**IMPORTANT:** Previous analysis used the WRONG column mapping. Correct mapping from CSV row 0:
- Col 16: G08 (was incorrectly mapped as G06)
- Col 19: G13 (was incorrectly mapped as G10)
- Full mapping: 1/2/G11 (4,7,10), 1/G08 (13,16), G13 (19), 1/2/3/4 FA (22,25,28,31), 1/2/G09 (34,37,40), G14 (43)

## Analysis

Ran `scripts/analyze/5hour-day-analysis.py` with the corrected column mapping.

**Result:**
- G08: 100% of days are 4–5 hours (never 6) → `maxHoursPerDay = 5`
- G13: 100% of days are 4–5 hours (never 6) → `maxHoursPerDay = 5`
- All other groups: have 6-hour days (standard schedule)

**Groups with ~75–80% 5-hour days (NOT truly 5-hour groups):**
- G07: 79% are 5-hour days (1 six-hour day: Giovedì 11 giu)
- G11: 75% are 5-hour days (1 six-hour day: Giovedì 04 giu)
- 4 FA: 84% are 5-hour days (5 six-hour days)
- G10: 67% are 5-hour days (8 six-hour days)
- G09: 45% are 5-hour days (27 six-hour days)

This is different from Friday's school-wide 5-hour schedule (08:00–13:00 vs 08:00–14:00 Mon–Thu).

## Implementation notes (as captured)

- `StudentGroup.maxHoursPerDay` field: existed at time of writing
- H11 constraint: enforced `maxHoursPerDay`
- `PreAssignmentValidator.hasH11Violation()`: added for pre-assignment validation (DB queries)
- `PreAssignmentContext.hasH11Violation()`: added for in-memory validation (fast path)
- `PreAssignmentContext.groupDailyHours` map (`key = "groupId|date"`, value = hour count): populated in `initialize()`, maintained by `trackLesson()`/`trackBlock()`, decremented by `untrackLesson()`/`untrackBlock()`; `hasH11Violation(...)` does O(1) lookup

> ⚠️ **Internal discrepancy (unresolved):** the original session note's "Implementation" section says `DataGenerator` was updated to set `maxHoursPerDay=5` for **G06, G10**, while the finding itself identifies **G08 and G13** as the 5-hour groups. This was not reconciled before capture. Verify which groups actually carry `maxHoursPerDay=5` in the current `DataGenerator` before acting. (See `.claude/rules/datagenerator-protection.md`.)
