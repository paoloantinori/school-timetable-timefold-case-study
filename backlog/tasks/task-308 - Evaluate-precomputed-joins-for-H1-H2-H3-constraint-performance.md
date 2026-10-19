---
id: TASK-308
title: Evaluate precomputed joins for H1/H2/H3 constraint performance
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
Timefold 2.0 introduced precomputed joins that cache static parts of Constraint Streams. Our H1/H2/H3 constraints use `forEach().join()` with equality joiners that have a large static component.\n\n**Opportunity:** The teacher/room/group equality join produces many static pairs recalculated every move. Precomputing the static join and only evaluating the timeslot-dependent filter per move could reduce score calculation time.\n\n**Reference:** claudedocs/research_timefold-upgrade-2.x_20260516.md
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Benchmark baseline for H1/H2/H3
- [ ] #2 Precomputed join implementation
- [ ] #3 Performance comparison documented
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Superseded by TASK-314. Evaluated precomputed joins — NOT beneficial for H1/H2/H3 because the expensive filter (timeslotsOverlap) depends on planning variables, not problem facts.
<!-- SECTION:FINAL_SUMMARY:END -->
