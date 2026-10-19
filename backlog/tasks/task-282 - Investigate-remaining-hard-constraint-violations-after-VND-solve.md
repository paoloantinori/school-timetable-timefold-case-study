---
id: TASK-282
title: Investigate remaining hard constraint violations after VND solve
status: Done
assignee: []
created_date: '2026-03-21 09:05'
updated_date: '2026-07-15 19:33'
labels:
  - claude-planned
  - solver
  - constraints
milestone: solver-optimization
dependencies:
  - TASK-280
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Even VND (the best config) produces -11 hard violations after 1800s. The construction heuristic + local search cannot reach zero hard violations. The biggest solver quality wins will come from understanding and reducing these remaining violations.

**Action:** 
1. Run VND solve and extract per-constraint hard violation breakdown via `/api/schedule/analysis/lessons`
2. Identify which specific hard constraints are being violated (H1-H7, H9, H11, H14-H19, H-M2, H-D1)
3. Determine if violations are caused by:
   - Insufficient timeslot capacity (fundamental infeasibility)
   - Pre-assignment pinning conflicts
   - Constraint interactions creating deadlocks
   - Solver not running long enough
4. For H17 (vertical compaction) specifically — this is the newest hard constraint (promoted from M13 in TASK-227) and may be the hardest to satisfy

**Dependency:** TASK-280 should be completed first so analysis reflects VND's behavior.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 VND solve completed with full constraint analysis
- [ ] #2 Per-constraint hard violation breakdown documented
- [ ] #3 Root cause identified for top 3 most-violated hard constraints
- [ ] #4 Recommendations for reducing hard violations (pre-assignment improvements, constraint relaxation, or data fixes)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
SUPERSEDED (triaged 2026-07-15): constraint set substantially refactored since (M21/M22/M23 added, H4.3 generalized, H17 promoted). This session's feasibility audit (TASK-321) + experiment results (TASK-324/325 negative) supersede the 'investigate violations' objective. The structural hard debt is documented (M22+H4.3+H4, data-driven).
<!-- SECTION:FINAL_SUMMARY:END -->
