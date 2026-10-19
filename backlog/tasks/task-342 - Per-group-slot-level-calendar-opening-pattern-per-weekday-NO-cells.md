---
id: TASK-342
title: 'Per-group slot-level calendar (opening pattern per weekday, NO cells)'
status: Done
assignee: []
created_date: '2026-07-16 09:33'
updated_date: '2026-07-16 19:03'
labels:
  - claude-planned
  - spalmone-2027
milestone: m-7
dependencies:
  - TASK-341
references:
  - claudedocs/spalmone-2026-27/analysis.md
  - scripts/analyze/spalmone2027-extract.py
priority: high
ordinal: 166000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: The spalmone grid closes individual slots per group via NO cells (analysis.md section 2): first-year groups have no afternoons and a 5-slot Friday; G14 runs only Thu (5 slots) + Fri (3 slots); FC and 3rd years use afternoons; the first school day starts at 09:00. Current model only has per-group maxHoursPerDay and a hardcoded 5h-Friday assumption - the real availability CANNOT be expressed.

WHAT:
1. Design a GroupCalendar concept: for each StudentGroup, which slotIndexes are open on each weekday, plus date-scoped overrides (e.g. first week). Recommended: new entity GroupCalendarRule {studentGroup, dayOfWeek, openSlotMask or minSlot/maxSlot, effectiveFrom, effectiveTo} with a service exposing isOpen(group, timeslot).
2. Derive the actual per-group patterns mechanically from claudedocs/spalmone-2026-27/data/lessons.csv (the extractor scripts/analyze/spalmone2027-extract.py already computes per-group weekday-slot occupancy - reuse its logic) and seed them.
3. Wire into: lesson generation (do not generate candidate placements in closed slots), H18 semantics (start-of-day = first OPEN slot for that group+day, not slot 0), H11 (max = number of open slots that day unless stricter), feasibility checks.
4. CRUD REST endpoints for the new entity (follow the pattern of StagePatternResource).

DEPENDS: TASK-341 (8-slot model must exist first).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 GroupCalendarRule entity + service isOpen(group,timeslot) exists with unit tests
- [x] #2 Seeded rules reproduce the real patterns: G14 Thu+Fri only, first-years no afternoon + short Friday, verified in a test against lessons.csv-derived expectations
- [x] #3 H18 and lesson generation consult the calendar; no lesson can be placed in a closed slot (hard or generation-level)
- [x] #4 CRUD endpoints exist with REST Assured test; ./scripts/core/test.sh passes
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented + code-review high (2 finder batches, 15 candidates, Opus-verified: 5 CONFIRMED fixed, incl. M20-vs-calendar structural conflict; 1 PLAUSIBLE fixed; REST validation added as deliberate deviation from no-validation house style). GroupCalendarRule entity + service (default-open, most-specific-wins, deterministic tie-break, closed-day sentinel), static snapshot for solver, VRP filtering, H20 safety net, calendar-aware M18 anchor + M20 quota cap, CRUD with validation, 2026/27 patterns seeded from golden data (cross-checked against extractor). Deferred: pre-assignment paths do not consult the calendar (annotated in TASK-364), AdaptiveSlotFinder stale slots (TASK-343), G14 pattern row (TASK-343). Tests 354/354.
<!-- SECTION:FINAL_SUMMARY:END -->
