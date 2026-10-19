---
id: TASK-335
title: 'EXPERIMENT: Benchmark fix-and-optimize vs single long run at equal wall-clock'
status: Done
assignee: []
created_date: '2026-07-12 17:47'
updated_date: '2026-08-14 09:30'
labels:
  - claude-planned
  - experiment
milestone: m-5
dependencies:
  - TASK-334
references:
  - claudedocs/research/critical-assessment-and-roadmap-20260712.md
priority: high
ordinal: 159000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
WHY: The milestone gate for m-5. The fix-and-optimize loop (TASK-334) must prove it beats simply running the existing pipeline longer. See roadmap doc section 4.

WHAT:
1. Arms, at equal TOTAL wall-clock (2 hours each), 2 repetitions per arm: (A) normal pipeline solve with phase caps raised to consume the full budget (respect TASK-326 findings for termination settings); (B) normal pipeline solve with standard termination, then fix-and-optimize consuming the remaining budget.
2. Standardized scorecards for all runs (TASK-319); comparison on conflict-hard subscore, unassigned count, H17 gaps, M20 sparse days, avg lessons/day per group vs spalmone targets, and the TASK-321 success criterion.
3. Record per-round acceptance rate and score trajectory of the fix-and-optimize arm (which neighborhood types produced improvements - informs selector tuning).
4. Append to experiment-log.md. Verdict: does arm B dominate arm A? If partially (e.g. better hard, worse soft), state the trade-off explicitly and recommend the default production mode.
5. If arm B wins, update CLAUDE.md How to Run section with the recommended invocation.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Two arms, two repetitions each, equal wall-clock, standardized scorecards
- [ ] #2 Per-neighborhood acceptance statistics captured for the fix-and-optimize arm
- [ ] #3 experiment-log.md updated; explicit verdict and recommended production mode documented
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
ASSORBITO da TASK-478: la validazione benchmark e' la catena di evidenza della ratifica (discriminatore offline UNCAPPED 5/5, probe LIVE 18/18 boxed depth 0..1, A/B finale 2/2 hard-0). Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->
