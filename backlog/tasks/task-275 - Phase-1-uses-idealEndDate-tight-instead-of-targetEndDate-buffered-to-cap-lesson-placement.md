---
id: TASK-275
title: >-
  Phase 1 uses idealEndDate (tight) instead of targetEndDate (buffered) to cap
  lesson placement
status: Done
assignee: []
created_date: '2026-03-20 04:33'
updated_date: '2026-07-15 19:32'
labels:
  - bug
  - claude-planned
dependencies: []
references:
  - src/main/java/com/school/planner/solver/DayClusteredConstructionPhase.java
  - src/main/java/com/school/planner/service/TargetEndDateCalculator.java
  - src/main/java/com/school/planner/domain/Lesson.java
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem
DayClusteredConstructionPhase was placing lessons beyond the "ideal end date" — the date shown by the UI color change. The root cause: Phase 1 was using `targetEndDate` (which includes BUFFER_WEEKS + 10% progressive extension) instead of `idealEndDate` (the tight date where cumulative slots first exceed total hours).

## Two Dates Explained
| Field | Computation | Used by |
|-------|------------|---------|
| `idealEndDate` (in `IdealEndDateInfo`) | First date where cumulative available slots ≥ totalHours. No buffer. | **UI coloring** (VirtualizedDayRow) |
| `targetEndDate` (on `StudentGroup`) | idealEndDate + BUFFER_WEEKS + 10% `ensureEnoughSlots()` extension. Capped at yearEnd. | **VRP filtering**, **M12 constraint** |

## Fix
Phase 1 now uses `idealEndDate` via `group.idealEndDateInfo.idealEndDate()` — the same tight date the UI uses for the color change. This means:
- Lessons placed by Phase 1 will not go past the colored boundary
- The VRP `targetEndDate` buffer is preserved for LS flexibility (LS can still move lessons slightly past ideal if needed to resolve hard constraints)
- Fallback chain: `idealEndDate` → `targetEndDate` → `2026-07-01` (for benchmark data where idealEndDateInfo may be null)

## Files Changed
- `src/main/java/com/school/planner/solver/DayClusteredConstructionPhase.java`
  - Added `getIdealEndDate(StudentGroup)` helper method
  - `findFirstAvailableDate()` and `findNextAvailableDay()` now use `getIdealEndDate()` instead of `group.targetEndDate`

## Testing
1. Start solver, watch Phase 1 logs — verify "Could not place N lessons" increases for groups with tight idealEndDates (G12, G13, G14)
2. Verify no Phase 1 lessons appear after the UI color-change boundary for any group
3. Compare M12 score vs previous runs (should decrease)
4. Verify fallback CH still handles overflow lessons correctly
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Phase 1 does not place lessons past targetEndDate for groups with bounded targets (G12, G13, G14, G10, etc.)
- [ ] #2 M12 violation count is measured and compared before/after the fix
- [ ] #3 Fallback CH (Phase 2) successfully places overflow lessons from capped Phase 1
- [ ] #4 Analysis endpoint confirms no lessons placed after targetEndDate by Phase 1 for bounded groups
- [ ] #5 For 990h groups, verify whether a separate 'school end' concept is needed vs timeslot range end
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
LIKELY_FIXED (triaged 2026-07-15): DayClusteredConstructionPhase:425 uses getIdealEndDate(group) for maxDate in both findFirstAvailableDate and findNextAvailableDay.
<!-- SECTION:FINAL_SUMMARY:END -->
