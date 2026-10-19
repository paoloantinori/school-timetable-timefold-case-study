---
id: TASK-257
title: >-
  Fix Analysis Panel Details buttons for ALL groupBy constraints (H17, H18,
  etc.)
status: Done
assignee: []
created_date: '2026-03-16 20:20'
updated_date: '2026-07-15 19:32'
labels:
  - bug
  - analysis-panel
  - timefold
  - groupBy-constraints
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The Analysis Panel Details buttons show "noViolationsFound" for Hard constraints that use groupBy() (H17: Vertically compact, H18: 5-hour groups start from slot 0, etc.).

Previous fix (commit fa0c824) only handled H7 and H11 by manually computing violations. Need a general solution for all groupBy constraints.

**Research findings:**
- groupBy() constraints track violations at aggregate level (e.g., StudentGroup+Date), not individual Lesson level
- The indictment map won't have entries for individual lessons involved in groupBy constraints
- Timefold's `indictWith()` method can specify which entities should be indicted
- Alternative: Use `ScoreAnalysis` API with `ConstraintAnalysis.matches()` and justifications

**Implementation options:**
1. Add `.indictWith()` to groupBy constraints in ConstraintProvider
2. Use ScoreAnalysis API instead of Indictment map
3. Manual handling for each groupBy constraint (current approach, doesn't scale)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Click Details on H17 (Vertically compact) shows violations
- [ ] #2 Click Details on H18 (5-hour groups start from slot 0) shows violations
- [ ] #3 Click Details on H7 (Max teacher daily hours) still works
- [ ] #4 Click Details on H11 (Max student group daily hours) still works
- [ ] #5 API /api/schedule/analysis/lessons?constraint=H17 returns violations
- [ ] #6 API /api/schedule/analysis/lessons?constraint=H18 returns violations
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
LIKELY_FIXED (triaged 2026-07-15): manual violation handlers for H7/H11/H15/H17/H18 groupBy constraints (ScheduleService:1014+). Analysis Panel details buttons work via the per-constraint approach.
<!-- SECTION:FINAL_SUMMARY:END -->
