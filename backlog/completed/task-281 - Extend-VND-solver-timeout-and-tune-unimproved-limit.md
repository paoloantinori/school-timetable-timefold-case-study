---
id: TASK-281
title: Extend VND solver timeout and tune unimproved limit
status: Done
assignee: []
created_date: '2026-03-21 09:05'
updated_date: '2026-08-14 09:30'
labels:
  - claude-planned
  - solver
milestone: solver-optimization
dependencies:
  - TASK-280
priority: medium
ordinal: 280
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
VND showed 30% higher calculation throughput (43k/s vs 33k/s for Tabu) and was still actively computing at the 1800s limit (77M calcs vs 60M). The current unimprovedSecondsSpentLimit of 600s may be cutting VND short if it has long improvement plateaus followed by jumps.

**Action:** After TASK-280 (switch to VND), experiment with:
- Longer overall timeout (e.g., 3600s) to leverage VND's faster throughput
- Tuning unimprovedSecondsSpentLimit — VND may benefit from a longer patience window since it explores structured neighborhoods rather than random moves
- Measure whether score improvement continues beyond 1800s or plateaus

**Dependency:** TASK-280 must be completed first (VND must be in production).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 TASK-280 completed (VND is production config)
- [ ] #2 Benchmark run with extended timeout (3600s)
- [ ] #3 Benchmark run with different unimproved limits (600, 900, 1200s)
- [ ] #4 Score comparison: extended vs baseline 1800s run
- [ ] #5 Recommendation documented for optimal timeout/unimproved settings
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
RULED-OUT dalla campagna metaheuristic (synthesis 2026-08-05): tuning di timeout/unimproved flat sullo staged; i limiti attuali (secondsSpentLimit 3600, unimproved 600, diminishedReturns) sono al ceiling. Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->
