---
id: TASK-179
title: Add precompute() for static constraint streams
status: In Progress
assignee: []
created_date: '2026-03-05 15:28'
updated_date: '2026-03-05 16:46'
labels:
  - solver
  - performance
  - claude-planned
milestone: Timefold Upgrade
dependencies:
  - TASK-177
references:
  - >-
    src/main/java/com/school/planner/solver/SchoolScheduleConstraintProvider.java
  - src/main/java/com/school/planner/solver/BaseConstraintProvider.java
documentation:
  - >-
    https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/score-calculation
  - >-
    https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/performance
priority: medium
ordinal: 8000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

Timefold 1.25+ introduces `constraintFactory.precompute()` for constraint streams that join on static (non-planning-variable) data. Precomputing these joins avoids re-evaluating them on every move, improving move evaluation speed.

## Candidate Constraints

| Constraint | Join Target | Why Precomputable |
|-----------|-------------|-------------------|
| H4 (Stage blackout) | StagePattern | StagePattern dates/dayMask are static problem facts |
| H5 (Teacher unavailability) | TeacherAvailability | Availability records are static |
| H6 (School holiday) | SchoolHoliday | Holiday dates are static |
| M5 (Informatica after stage) | StagePattern.endDate | Stage end dates are static |

## Approach

For each candidate constraint:
1. Identify the static join (e.g., `Lesson.join(StagePattern)`)
2. Extract the static part into `precompute()`
3. Keep the dynamic filter (planning variable dependent) in the main stream
4. Measure move evaluation speed before/after

## Important Notes

- Always benchmark before/after — precompute() has overhead and only helps for large datasets
- Only precompute joins where the problem fact data is truly static during solving
- The `precompute()` API is still evolving — check 1.31 docs for latest syntax
- Do NOT precompute constraints that join on planning entities or shadow variables
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 At least H4 and H5 constraints use precompute() API
- [ ] #2 All 192 tests pass (constraint verifier tests validate correctness)
- [ ] #3 Move evaluation speed measured before and after (logged in implementation notes)
- [ ] #4 No score corruption — constraint scores match non-precomputed baseline
<!-- AC:END -->
