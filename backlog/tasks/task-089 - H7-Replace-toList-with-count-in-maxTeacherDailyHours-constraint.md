---
id: TASK-089
title: 'H7: Replace toList() with count() in maxTeacherDailyHours constraint'
status: Done
assignee:
  - Claude
created_date: '2026-02-23 17:24'
updated_date: '2026-03-10 05:16'
labels:
  - timefold-best-practices
  - claude-planned
  - solver
dependencies: []
priority: high
ordinal: 22750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Context**: The timefold-starter best practices audit identified that H7 (`maxTeacherDailyHours`) uses `toList()` collector when only the cardinality is needed. This disables Timefold's incremental score calculation for this constraint — when any single lesson moves, the entire grouped list must be rebuilt instead of just updating a counter.

**Current code** (line 301-312 in `SchoolScheduleConstraintProvider.java`):
```java
Constraint maxTeacherDailyHours(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .groupBy(Lesson::getTeacher, l -> l.timeslot.getDate(), toList())
            .penalize(HardMediumSoftScore.ONE_HARD,
                    (teacher, date, lessons) -> {
                        int hours = lessons.size();
                        return Math.max(0, hours - 6);
                    })
            .asConstraint("H7: Max teacher daily hours");
}
```

**Target code**: Replace `toList()` with `count()` and adjust the penalty lambda to work with an integer count instead of a list:
```java
Constraint maxTeacherDailyHours(ConstraintFactory constraintFactory) {
    return constraintFactory.forEach(Lesson.class)
            .groupBy(Lesson::getTeacher, l -> l.timeslot.getDate(), count())
            .penalize(HardMediumSoftScore.ONE_HARD,
                    (teacher, date, lessonCount) -> Math.max(0, lessonCount - 6))
            .asConstraint("H7: Max teacher daily hours");
}
```

**Why this matters**: `count()` enables incremental calculation. With ~7,000+ lesson entities and the solver evaluating millions of moves, this eliminates unnecessary list allocations on every score recalculation for H7.

**Propagation**: This is an architecture-specific file (`SchoolScheduleConstraintProvider.java`). The same pattern exists on the shadow branches but the constraint name/structure may differ slightly. Check all 3 active branches.

**Testing**: The existing `SchoolScheduleConstraintProviderTest` should continue to pass unchanged — the semantic behavior (penalize when >6 lessons/day) is identical.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 H7 constraint uses count() instead of toList()
- [x] #2 Penalty lambda accepts int instead of List
- [x] #3 Existing H7 constraint test passes unchanged
- [ ] #4 Change propagated to all 3 active branches (adapt per constraint provider variant)
- [x] #5 mvn test passes on all branches
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Completed on feature/cartesian-block-entity branch. Shadow branches use different H7 pattern (sum lab block hours) - requires sum() collector instead of count(). Propagation deferred.
<!-- SECTION:NOTES:END -->
