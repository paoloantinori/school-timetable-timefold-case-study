---
id: TASK-121.02
title: Create TeacherDayAssignment data class (replace RotationCandidate)
status: Done
assignee: []
created_date: '2026-02-26 09:53'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - pre-assignment
  - phase1a
  - subtask
dependencies: []
parent_task_id: TASK-121
priority: high
ordinal: 115000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

`RotationCandidate` currently maps `BlockKey(teacherId, studentGroupId) → DayOfWeek`. The correct decision variable is the teacher, not the (teacher, group) pair. All of a teacher's group pairs inherit the same day assignment.

## Implementation

### Create `TeacherDayAssignment.java`:

```java
/**
 * A complete assignment of weekdays to teachers for a single room tag.
 * Each teacher has exactly one day; all their (teacher, group) blocks
 * are scheduled on that day.
 */
public record TeacherDayAssignment(
    Map<Long, DayOfWeek> teacherDays,  // teacherId → assigned day
    int assignmentNumber               // for logging/debugging
) {
    /** Get all BlockKeys for a teacher and their assigned day. */
    public DayOfWeek getDayForTeacher(Long teacherId) {
        return teacherDays.get(teacherId);
    }
    
    /** Number of teachers assigned. */
    public int size() {
        return teacherDays.size();
    }
}
```

### Modify `RotationCandidate.BlockKey`:

Keep `BlockKey` as-is (it's useful for block lookup), but it should no longer be the key in candidate assignments. Move it to a shared location or keep in `RotationCandidate` for backward compatibility during migration.

### Keep `RotationCandidate` temporarily:

Don't delete yet — the `RotationTestRunner` still references it. It will be removed in the cleanup task after the greedy assigner is wired in.

## Files to Create
- `service/TeacherDayAssignment.java`

## Files to Modify
- None yet (wiring happens in task-121.3 and task-121.5)

## Verification
```bash
mvn compile -DskipTests
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 TeacherDayAssignment record created with Map<Long, DayOfWeek> teacherDays
- [ ] #2 BlockKey record preserved for block lookup compatibility
- [ ] #3 Compiles cleanly with no downstream breakage
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Completed 2026-02-26 as part of task-121 implementation.

TeacherDayAssignment record created at:

src/main/java/com/school/planner/service/TeacherDayAssignment.java
<!-- SECTION:NOTES:END -->
