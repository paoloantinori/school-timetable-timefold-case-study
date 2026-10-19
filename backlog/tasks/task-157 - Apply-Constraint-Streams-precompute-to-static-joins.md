---
id: TASK-157
title: Apply Constraint Streams precompute() to static joins
status: Done
assignee: []
created_date: '2026-03-03 20:47'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - performance
  - solver
milestone: Solver Performance
dependencies:
  - TASK-156
references:
  - claudedocs/research-timefold-optimizations-2026-03-03.md
  - 'src/main/java/com/school/planner/solver/BaseConstraintProvider.java:225-300'
documentation:
  - >-
    https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/score-calculation
  - >-
    https://docs.timefold.ai/timefold-solver/latest/constraints-and-score/performance
priority: medium
ordinal: 80000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Use the Constraint Streams `precompute()` API to cache joins/filters that depend only on problem facts (not planning variables). This avoids re-evaluating static join conditions on every move.

**Best candidates in our constraint provider:**

| Constraint | Join Pattern | Why Precomputable |
|---|---|---|
| H4.1/H4.2 (Stage blackout) | `Lesson/LabBlock ⋈ StagePattern` on studentGroup | StagePattern is a problem fact; studentGroup is an entity property (not a variable) |
| H5.1/H5.2 (Teacher unavail.) | `Lesson/LabBlock ⋈ TeacherAvailability` on teacher | TeacherAvailability is a problem fact; teacher is an entity property |
| H5.3/H5.4 (Doppio unavail.) | `Lesson/LabBlock ⋈ TeacherAvailability` on doppioTeacher | Same rationale |
| M5 (Informatica after stage) | `Lesson ⋈ StagePattern` | Course and group properties are static |
| H-D1.1-H-D1.4 (Doppio conflicts) | `Lesson/LabBlock ⋈ Lesson/LabBlock` on doppioTeacher | doppioTeacher is a problem fact property |

**How it works:**
```java
// Before: re-evaluates join on every move
constraintFactory.forEach(Lesson.class)
    .join(TeacherAvailability.class,
        Joiners.equal(Lesson::getTeacher, ta -> ta.teacher))
    .filter(...)

// After: join precomputed once at solver start
constraintFactory.precompute(
    constraintFactory.forEach(Lesson.class),
    constraintFactory.forEach(TeacherAvailability.class),
    Joiners.equal(Lesson::getTeacher, ta -> ta.teacher))
    .filter(...)  // only this re-evaluates on moves
```

**Caveat:** Precomputed streams include unassigned entities. Must add post-precompute filter to check variable assignment if needed.

**Requires:** Timefold >= 1.29.0 (BOM) — see TASK-156.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 H4, H5, M5, and H-D1 constraints refactored to use precompute() API
- [ ] #2 Post-precompute filters handle unassigned entities correctly
- [ ] #3 All 193 tests pass with identical scores
- [ ] #4 Benchmark shows improved move evaluation speed
- [ ] #5 Score explanation still works correctly (precompute is transparent to explanations)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Duplicate of TASK-179 (Add precompute() for static constraint streams). TASK-179 is already in progress - consolidating work under that task.
<!-- SECTION:FINAL_SUMMARY:END -->
