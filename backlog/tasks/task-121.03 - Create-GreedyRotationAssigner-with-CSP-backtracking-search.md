---
id: TASK-121.03
title: Create GreedyRotationAssigner with CSP backtracking search
status: Done
assignee: []
created_date: '2026-02-26 09:53'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - pre-assignment
  - phase1a
  - solver
  - csp
  - subtask
dependencies:
  - task-121.01
  - task-121.02
parent_task_id: TASK-121
priority: high
ordinal: 116000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

Core task of task-121. Replace the flat Cartesian product enumeration with a greedy, per-teacher assignment algorithm using backtracking.

**See Serena memory**: `hybrid-phase1a/search-space-analysis-task120`

## Algorithm

```
function assignTeachers(teachers, roomTag):
  // Order: most-constrained first (fewest valid days)
  sort teachers by |validDays| ascending
  
  return backtrack(0, teachers, {}, context)

function backtrack(index, teachers, assignment, context):
  if index == teachers.length:
    return SUCCESS(assignment)
  
  teacher = teachers[index]
  
  for day in teacher.validDays:
    // Room exclusivity: skip if another teacher already has this day
    //   (only for full-day blocks sharing same room)
    if roomExclusivity and dayAlreadyTaken(day, assignment, roomTag):
      continue
    
    // Try assigning all this teacher's blocks to this day
    result = testTeacherBlocks(teacher, day, context)
    
    if result.success:
      // Track in context (for subsequent teachers to see)
      trackAssignments(result.assignments, context)
      assignment[teacher.id] = day
      
      // Recurse to next teacher
      subResult = backtrack(index + 1, teachers, assignment, context)
      
      if subResult.success:
        return subResult
      
      // Backtrack: undo this teacher's assignments
      untrackAssignments(result.assignments, context)
      assignment.remove(teacher.id)
  
  return FAILURE(teacher, "No valid day found")
```

## Implementation Details

### `GreedyRotationAssigner.java`:

1. **Teacher extraction**: Group `LabRotation` records by teacher (not by (teacher, group) pair)
2. **Valid days**: Use same logic as current `computeValidDays()` — external teachers get 1 day, others get 5
3. **Room exclusivity pruning**: For full-day blocks (blockSize=6 and slotStart=0), at most 1 teacher can use a room per day. Skip days where another teacher is already assigned. Partial-day blocks (blockSize < total slots) can coexist.
4. **Per-teacher block testing**: Reuse `RotationTestRunner.findValidSlot()` and `checkConflicts()` for each block of the teacher
5. **Backtracking**: Use `PreAssignmentContext.untrackBlock()` (already implemented and verified correct)
6. **Status tracking**: Update `currentCandidatesTried` and `currentPhase` for polling

### Integration with existing code:

- Call `RotationTestRunner` methods for slot-level testing (reuse, don't rewrite)
- Use `PreAssignmentContext` for conflict tracking (already O(1))
- Return `List<RotationTestRunner.SlotAssignment>` for compatibility with `pinAssignments()`

## Search Space Comparison

| RoomTag | Current (Cartesian) | Greedy CSP | Speedup |
|---------|-------------------|------------|---------|
| acconciatura | 390,625 | ≤12 | 32,552× |
| moda | ~625 | ≤5 | 125× |
| meccanica | ~125 | ≤5 | 25× |
| termoidraulica | ~25 | ≤5 | 5× |
| informatica | ~3,125 | ≤25 | 125× |

## Files to Create
- `service/GreedyRotationAssigner.java`

## Dependencies
- task-121.1 (Timeslot cache — needed for fast slot testing)
- task-121.2 (TeacherDayAssignment data class)

## Verification
```bash
mvn test -Dtest=GreedyRotationAssignerTest
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Groups LabRotation records by teacher (not by (teacher, group) pair)
- [ ] #2 Orders teachers by constraint tightness (fewest valid days first)
- [ ] #3 Assigns one teacher at a time with slot-level validation via RotationTestRunner
- [ ] #4 Backtracks correctly when no valid day exists for a teacher
- [ ] #5 Room exclusivity pruning skips days where room is fully occupied
- [ ] #6 Returns List<SlotAssignment> compatible with existing pinAssignments()
- [ ] #7 Unit tests cover: success path, backtracking path, external-teacher-only path, room exclusivity
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Completed 2026-02-26 as part of task-121 implementation.

GreedyRotationAssigner created at:

src/main/java/com/school/planner/service/GreedyRotationAssigner.java

Features:

- Teacher-centric ordering (external teachers first)

- H5 availability filtering

- CSP backtracking with dead-end detection

- Room exclusivity pruning
<!-- SECTION:NOTES:END -->
