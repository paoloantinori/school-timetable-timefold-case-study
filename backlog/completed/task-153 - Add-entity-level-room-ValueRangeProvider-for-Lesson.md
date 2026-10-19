---
id: TASK-153
title: Add entity-level room ValueRangeProvider for Lesson
status: Done
assignee: []
created_date: '2026-03-03 20:46'
updated_date: '2026-03-07 21:19'
labels:
  - claude-planned
  - performance
  - solver
milestone: Solver Performance
dependencies: []
references:
  - claudedocs/research-timefold-optimizations-2026-03-03.md
  - src/main/java/com/school/planner/domain/Lesson.java
  - 'src/main/java/com/school/planner/domain/LabBlock.java:178-210'
documentation:
  - >-
    https://docs.timefold.ai/timefold-solver/latest/using-timefold-solver/modeling-planning-problems
  - >-
    https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/performance
priority: high
ordinal: 63000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add an entity-level `@ValueRangeProvider` on `Lesson` for room filtering, matching the pattern already used by `LabBlock.getAvailableRooms()`.

Currently `Lesson` uses the solution-level room VRP (all ~20 rooms), while `LabBlock` already filters rooms by `requiredRoomTags`. This means the solver wastes moves trying invalid room assignments that would immediately violate H-M2 (room suitability).

**What to filter:**
- Lab lessons (`course.isLab && course.requiredRoomTags != null`) → only rooms with matching tags (reduces ~20 → ~2-3)
- Non-lab lessons → exclude rooms that have tags (lab-only rooms) — enforced by H-M2b (reduces ~20 → ~15)
- Pinned lessons → only assigned room (same pattern as LabBlock)

**Why this is safe:** Room suitability (H-M2) is a HARD constraint. Entity-level VRP to enforce hard constraints is explicitly recommended by Timefold docs. The solver never needs to temporarily break H-M2 to escape local optima.

**Timefold docs caveat:** "A ValueRangeProvider on the planning entity consumes more memory... and disables certain automatic performance optimizations." Mitigated by caching (same pattern as LabBlock).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Lesson.java has `getAvailableRooms()` with `@ValueRangeProvider(id = "availableRooms")` and caching
- [ ] #2 Lesson.room `@PlanningVariable` references `valueRangeProviderRefs = {"availableRooms"}`
- [ ] #3 Lab lessons only see rooms with matching requiredRoomTags
- [ ] #4 Non-lab lessons exclude lab-only rooms (rooms with tags)
- [ ] #5 Pinned lessons return only their assigned room
- [ ] #6 All existing tests pass (193 tests)
- [ ] #7 Pre-assignment achieves 100% pinning (1135/1135)
- [ ] #8 Benchmark shows CH speed improvement vs baseline
<!-- AC:END -->
