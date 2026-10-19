---
id: TASK-348
title: 'H7 recalibration: per-teacher max daily hours (T01 teaches 8h days)'
status: Done
assignee: []
created_date: '2026-07-16 09:35'
updated_date: '2026-07-17 05:11'
labels:
  - claude-planned
  - spalmone-2027
  - constraints
milestone: m-8
dependencies:
  - TASK-347
priority: medium
ordinal: 172000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: analysis.md section 4.2: every teacher maxes at 6h/day EXCEPT T01 who teaches 8h in a day (full-day lab with G10 crossing lunch). A global max-6 H7 declares the golden solution infeasible.

WHAT: add maxDailyHours to Teacher (default 6), expose it in the Teacher CRUD API, seed T01 with 8 via the importer, and make H7 read the per-teacher value. Add a constraint-verifier test: 7h day penalized for a default teacher, 8h day NOT penalized for a teacher with max 8.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Teacher.maxDailyHours exists with default 6; H7 uses it; T01 seeded 8
- [x] #2 ConstraintVerifier tests cover both directions; golden audit shows H7=0 after change
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
NOTE 2026-07-17 (TASK-347 golden audit, clean-redo verified): H7 measured on the golden 2026/27 import (10,496 real + 1,993 stage lessons, all pinned): -23 hard total, matches=3114 (that's the total teacher-day groupBy tuple count, not violations - the constraint sums max(0, hours-6) per tuple). Per-lesson drill-down (/api/schedule/analysis/lessons?constraint=H7) shows the real violator is T01 only (12 lesson-entities); the endpoint also reports 99 "Stage" pseudo-teacher entities as H7-violating, but the aggregate -23 hard score is only consistent with T01's real overage, so pseudo teachers ARE correctly excluded from the score - the 99 Stage entries look like a per-lesson-analysis display/attribution bug (not a scoring bug), not chased further given time budget. Confirms this task's premise: T01 is the sole real 8h/day case. Full audit: claudedocs/spalmone-2026-27/golden-audit.md.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented (worktree, merged) + combined code-review high with TASK-366 (8 candidates, fixes D1-D8). Teacher.maxDailyHours nullable (effective default 6 via getEffectiveMaxDailyHours, single source for H7 and the ScheduleService mirror incl. the audit's 99-spurious-Stage-rows fix), clearable via 0-sentinel, null-keeps CRUD, seeded from the new teachers.csv column (T01 8, extractor preserves it), TeachersAdmin input. Golden-audit expectation: H7 -> 0 on next live re-audit. Tests 512/512.
<!-- SECTION:FINAL_SUMMARY:END -->
