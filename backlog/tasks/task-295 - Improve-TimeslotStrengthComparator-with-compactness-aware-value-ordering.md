---
id: TASK-295
title: Improve TimeslotStrengthComparator with compactness-aware value ordering
status: Done
assignee: []
created_date: '2026-03-22 05:39'
labels:
  - claude-planned
  - ls-optimization
  - solver
milestone: solver-optimization
dependencies: []
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Enhance `TimeslotStrengthComparator` to bias value selection toward timeslots that improve compactness, rather than just using natural chronological ordering.

## Why This Matters

The current `TimeslotStrengthComparator` uses `Timeslot.compareTo()` (natural ordering by startDateTime). This means during both CH and LS, earlier timeslots are always preferred. While this provides a reasonable default, it doesn't consider:

1. **Sparse day priority**: Timeslots on days that already have lessons but are underfilled should be preferred over timeslots on already-full days. A group with 3 hours on Monday and 0 on Tuesday should prefer Tuesday slots (to fill Tuesday) over adding a 4th hour to Monday.
2. **Group density awareness**: If a group has many lessons adjacent to a specific timeslot, moving a lesson there maintains compactness (H17).
3. **Teacher availability density**: Days with more available slots for the teacher should be preferred.

This affects both CH value ordering (which timeslot to try first) and LS move quality (which swap moves to evaluate first).

**Important**: This comparator is used via `@PlanningVariable(strengthComparatorClass)` on Lesson.timeslot. It must be deterministic and cannot depend on working solution state (since it's a value comparator, not an entity comparator).

## Implementation Approach

Since the comparator cannot access solution state, improvements must be static/precomputed:
- **Day-of-week weighting**: Prefer timeslots on days with fewer total school hours (Friday=5h vs Mon-Thu=6h — prefer Friday to fill it first)
- **Week-relative ordering**: Prefer earlier weeks over later weeks (maintains compact calendar)
- **Time-of-day weighting**: Prefer earlier slots within a day (maintains vertical compactness from top)

The comparator already does chronological ordering, so the main improvement would be adding **cross-entity awareness** — but since that's not possible in a static comparator, the alternative is to precompute per-group "sparse day" information and use it in a custom ValueRangeProvider filter.

**Alternative approach**: Instead of improving the static comparator, implement a custom `SelectionSorterWeightFactory` that assigns higher weights to timeslots on sparse days for the entity's student group. This IS solution-aware.

## References

- Research: `claudeodocs/research_timefold_ls_optimization_20260322.md` — Entity selectors for LS, value ordering
- Research: `claudeodocs/research_local_search_metaheuristics_timetabling_20260322.md` — Adaptive value selection strategies
- Code: `src/main/java/com/school/planner/domain/TimeslotStrengthComparator.java`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 TimeslotStrengthComparator enhanced with compactness-aware ordering (or SelectionSorterWeightFactory implemented as alternative)
- [ ] #2 Comparator remains deterministic and does not depend on mutable solution state
- [ ] #3 Rebuild succeeds and solver runs without errors
- [ ] #4 Benchmark: compare CH phase quality with improved vs current comparator
- [ ] #5 Verify H17/M20 scores improve during CH phase
<!-- AC:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
