---
id: TASK-336
title: >-
  EXPERIMENT: Rolling-horizon decomposition prototype (conditional - only if
  TASK-335 misses target)
status: Done
assignee: []
created_date: '2026-07-12 17:48'
updated_date: '2026-08-14 09:30'
labels:
  - claude-planned
  - experiment
  - design
milestone: m-5
dependencies:
  - TASK-335
references:
  - claudedocs/research/critical-assessment-and-roadmap-20260712.md
priority: low
ordinal: 160000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: Fallback architecture if fix-and-optimize (TASK-335) does not reach the TASK-321 success criterion. Timefolds documented continuous planning pattern (rolling window with locked prefix) decomposes long horizons: solve September-December fully with January-June as draft, lock the first term, roll the window forward. Each sub-problem is dramatically smaller (roughly one third of 8700 lessons), which changes the search-space class entirely. Trade-off to evaluate honestly: hour-distribution constraints spanning the whole year (S9 dilution, M12 overflow, target end dates, stage-relative rules like M5) lose global visibility - the design must state how each year-spanning constraint behaves under decomposition before any code is written.

WHAT:
1. GATE: skip and close if TASK-335 met the success criterion.
2. Design first (claudedocs/design/rolling-horizon.md): window/lock scheme aligned to trimesters (LabRotation periods already define natural boundaries), per-constraint analysis table (each of the ~30 constraints: unaffected / needs-window-scaling / broken-by-decomposition plus mitigation), hour-budget allocation per window (each course needs a per-trimester hour quota - derive from totalHours proportionally to available slots per trimester from the TASK-321 audit).
3. Prototype minimally: a service method that restricts the value range to window timeslots (the existing per-lesson getAvailableTimeslots filtering is the natural injection point) and pins everything outside the window, reusing FixAndOptimizeService pin machinery from TASK-334.
4. EXPERIMENT: full-year via 3 rolled windows vs the TASK-335 winner, equal total wall-clock, standardized scorecards, 2 repetitions.
5. Append to experiment-log.md with verdict.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Executed only if TASK-335 missed the success criterion (otherwise closed with reference)
- [ ] #2 Design doc with complete per-constraint decomposition-impact table before any code
- [ ] #3 Prototype reuses TASK-334 pin machinery; A/B vs TASK-335 winner at equal wall-clock
- [ ] #4 experiment-log.md updated with verdict
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
WON'T-DO: condizionale a "TASK-335 manca il target"; ExactFinisher (TASK-478) chiude il residuo boxed deterministicamente sul worst case noto, la condizione non si e' mai attivata. Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->
