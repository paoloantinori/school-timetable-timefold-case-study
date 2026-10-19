---
id: TASK-309
title: Evaluate toConsecutiveSequences for H17/H15 constraints
status: Done
assignee: []
created_date: '2026-05-16 04:26'
updated_date: '2026-05-21 20:56'
labels:
  - performance
  - solver
milestone: m-0
dependencies:
  - TASK-306
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Timefold 2.0 introduced `ConstraintCollectors.toConsecutiveSequences()` for consecutive sequence constraints. Two of our constraints could benefit:\n\n- H17 (Vertically Compact): Currently uses manual gap detection\n- H15 (Same-day same-subject consecutive): Uses pair joins to detect non-consecutive lessons\n\n**Reference:** claudedocs/research_timefold-upgrade-2.x_20260516.md
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 API fit evaluation for H17 and H15
- [ ] #2 Implementation if beneficial
- [ ] #3 Performance comparison
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Superseded by TASK-313. H17 and H15 already use toConsecutiveSequences. S7 evaluated — should remain with manual joins due to Duration-based adjacency semantics.
<!-- SECTION:FINAL_SUMMARY:END -->
