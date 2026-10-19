---
id: TASK-314
title: Implement precomputed joins for H1 (teacher conflict)
status: Done
assignee: []
created_date: '2026-05-21 17:07'
updated_date: '2026-06-16 10:41'
labels:
  - solver
  - performance
  - constraints
milestone: m-1
dependencies: []
priority: high
ordinal: 138000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Use Timefold 2.x `PrecomputeFactory` to cache the static join results for H1 (teacher conflict), our most expensive constraint.

**Problem**: H1 performs an O(n²) unique pair join on 8,000+ lessons, filtering by same teacher AND same timeslot. Most of the pair comparisons are wasted — only pairs sharing a timeslot matter. The timeslot matching is static (doesn't change during move evaluation for most lessons).

**Solution**: Use `constraintFactory.precompute()` to precompute lesson pairs that share a timeslot, then filter by teacher at evaluation time. This dramatically reduces the work per move.

**Approach**:
1. Create a `PrecomputeFactory` method that joins lesson pairs on same timeslot
2. In H1 constraint, use `constraintFactory.precompute()` wrapping the precomputed stream
3. Filter by teacher equality at evaluation time
4. Benchmark move evaluation speed before/after

**If successful, extend to**: H2 (room conflict), H3 (student group conflict)

**Files**: `src/main/java/com/school/planner/solver/BaseConstraintProvider.java` (H1 method, precompute method)

**Reference**: claudedocs/research_timefold-2.x-features-evaluation_20260521.md
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 H1 uses constraintFactory.precompute() with PrecomputeFactory
- [ ] #2 Benchmark shows >= 20% improvement in move evaluation speed
- [ ] #3 Constraint violations are identical (no regression)
- [x] #4 Document whether to extend to H2 and H3
<!-- AC:END -->



## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Evaluated precomputed joins for H1/H2/H3 — NOT beneficial for our constraints.

Current H1 already uses hash-indexed Joiners.equal(Lesson::getTeacher) on a problem fact. The expensive part (timeslotsOverlap filter) depends on planning variables (timeslot), which change on every move and cannot be precomputed. Timefold's delta-based evaluation already handles incremental re-evaluation efficiently.

The research doc's suggestion ("precompute lesson pairs that share a timeslot") is incorrect because timeslot IS a planning variable — precompute is designed for problem facts only.

Without constraint profiling (Enterprise-only, TASK-315), we can't measure actual bottlenecks. No code changes made.
<!-- SECTION:FINAL_SUMMARY:END -->
