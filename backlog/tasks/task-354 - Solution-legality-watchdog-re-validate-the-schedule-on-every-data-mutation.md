---
id: TASK-354
title: 'Solution legality watchdog: re-validate the schedule on every data mutation'
status: Done
assignee: []
created_date: '2026-07-16 09:37'
updated_date: '2026-07-17 04:07'
labels:
  - claude-planned
  - spalmone-2027
  - coherence
milestone: m-9
dependencies:
  - TASK-341
priority: high
ordinal: 178000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: user requirement (2026-07-16): if constraint-relevant data changes (availability, holidays, hours, stage windows, calendars), currently-legal solutions can silently become illegal. We must notice IMMEDIATELY, not at the next solve.

WHAT:
1. Backend: a ValidationService that scores the CURRENT schedule via the existing analysis machinery (SolutionManager.analyze) WITHOUT solving, returning per-constraint violation counts and the delta vs the last stored snapshot. Persist a small snapshot (constraint -> count, timestamp, trigger) after every run.
2. Trigger: after any mutating CRUD call on constraint-relevant entities (teachers, availability, holidays, stage patterns, group calendars, courses, rooms) schedule a debounced re-validation (e.g. 2s) so bulk edits validate once. CDI event or interceptor - pick the least invasive pattern consistent with the codebase.
3. Surface: endpoint GET /api/schedule/validation/latest returning current counts + NEW violations since previous snapshot (the newly-illegal diff). Frontend: red badge in the header when new hard violations appeared, with a panel listing them (reuse AnalysisPanel components).
4. Tests: REST Assured - edit a holiday onto a scheduled date, assert the watchdog reports new H6 violations naming the affected lessons.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Mutations on all listed entity types trigger debounced re-validation; verified by test
- [x] #2 Latest-validation endpoint returns counts plus newly-illegal diff vs previous snapshot
- [x] #3 UI badge + panel show new hard violations without running the solver
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented (worktree, merged) + code-review high (12 candidates, fixes B1-B9). Single ContainerResponseFilter fires debounced (2s) revalidation on all constraint-relevant CRUD + imports + reset + solve-completion (INTERNAL event); exclusions explicit and enforced by a reflective exhaustiveness test; ValidationService rescores without solving, persists last 20 snapshots, baseline-aware newly-violated diff; GET /validation/latest + POST /run; header badge (amber=hard present, red=new hard, baseline-aware) + panel. Known gap documented: pre-assignment completion (background-thread pinning) has no INTERNAL trigger yet — noted for TASK-367/351. Tests 494/494.
<!-- SECTION:FINAL_SUMMARY:END -->
