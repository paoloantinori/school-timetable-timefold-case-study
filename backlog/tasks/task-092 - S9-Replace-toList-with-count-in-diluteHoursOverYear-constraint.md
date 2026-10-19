---
id: TASK-092
title: 'S9: Replace toList() with count() in diluteHoursOverYear constraint'
status: Done
assignee: []
created_date: '2026-02-23 17:25'
updated_date: '2026-03-10 05:16'
labels:
  - timefold-best-practices
  - claude-planned
  - solver
dependencies: []
priority: medium
ordinal: 30750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**Context**: The timefold-starter best practices audit flagged `toList()` usage in S9 (`diluteHoursOverYear`, line 692-704). This constraint penalizes having >1 lesson of the same subject per week for a student group. It currently collects all matching lessons into a list, only to call `.size()` on it.

**Current code**:
```java
Constraint diluteHoursOverYear(ConstraintFactory constraintFactory) {
    WeekFields weekFields = WeekFields.of(Locale.getDefault());
    return constraintFactory.forEach(Lesson.class)
            .filter(lesson -> !lesson.pinned && lesson.timeslot != null)
            .groupBy(
                    Lesson::getStudentGroup,
                    Lesson::getSubject,
                    l -> l.timeslot.getDate().get(weekFields.weekOfWeekBasedYear()),
                    toList())
            .filter((group, subject, week, lessons) -> lessons.size() > 1)
            .penalize(HardMediumSoftScore.ofSoft(10),
                    (group, subject, week, lessons) -> lessons.size() - 1)
            .asConstraint("S9: Dilute hours over year");
}
```

**Target code**: Replace `toList()` with `count()`:
```java
Constraint diluteHoursOverYear(ConstraintFactory constraintFactory) {
    WeekFields weekFields = WeekFields.of(Locale.getDefault());
    return constraintFactory.forEach(Lesson.class)
            .filter(lesson -> !lesson.pinned && lesson.timeslot != null)
            .groupBy(
                    Lesson::getStudentGroup,
                    Lesson::getSubject,
                    l -> l.timeslot.getDate().get(weekFields.weekOfWeekBasedYear()),
                    count())
            .filter((group, subject, week, lessonCount) -> lessonCount > 1)
            .penalize(HardMediumSoftScore.ofSoft(10),
                    (group, subject, week, lessonCount) -> lessonCount - 1)
            .asConstraint("S9: Dilute hours over year");
}
```

**Why**: Same rationale as task-089 (H7). The list contents are never inspected — only `.size()` is called. Using `count()` enables incremental calculation.

**Propagation**: Architecture-specific file — adapt per branch family.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 S9 constraint uses count() instead of toList()
- [x] #2 Filter and penalty lambdas work with int instead of List
- [x] #3 Existing S9 constraint tests pass (if any)
- [x] #4 mvn test passes
- [x] #5 Change propagated to all 3 active branches
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Propagated to cartesian-block-entity only. Skipped propagation to other branches per user request.
<!-- SECTION:NOTES:END -->
