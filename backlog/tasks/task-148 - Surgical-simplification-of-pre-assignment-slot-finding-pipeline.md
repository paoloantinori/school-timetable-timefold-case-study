---
id: TASK-148
title: Surgical simplification of pre-assignment slot-finding pipeline
status: Done
assignee: []
created_date: '2026-03-03 09:33'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - pre-assignment
  - wont-fix
dependencies:
  - TASK-122
references:
  - claudedocs/pre-assignment-algorithms.md
  - claudedocs/human-planner-heuristics.md
  - src/main/java/com/school/planner/service/HybridPhase1aService.java
  - src/main/java/com/school/planner/service/GreedyRotationAssigner.java
  - src/main/java/com/school/planner/service/RotationTestRunner.java
  - src/main/java/com/school/planner/service/PreAssignmentContext.java
documentation:
  - .claude/plans/resilient-hopping-flame.md
priority: high
ordinal: 104000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The pre-assignment pipeline (HybridPhase1aService) fails to reliably pin lab blocks. Root cause is NOT the backtracking algorithm (which is sound at 12-25 candidates) but three specific failure points underneath it:

1. **NULL labRotation cascade**: `loadBlocksByRoomTag()` silently leaves blocks with `labRotation=null`. This causes `getValidDaysForTeacher()` to give internal teachers 5 valid days instead of 1, exploding the search space.

2. **Heavyweight slot finding**: `findValidSlotForBlock()` creates a RotationCandidate and calls `testRunner.testCandidate()` → `generateSlotCandidates()` iterating ALL dates in the period, for EVERY single block. Should use PreAssignmentContext O(1) checks directly.

3. **Redundant slot-finding in pinning**: `pinTeacherDayAssignment()` calls `findValidSlotForBlock()` AGAIN per block after the assigner already found slots.

**Approach**: Keep GreedyRotationAssigner backtracking structure (preserves 122.2 design), fix data linkage, replace slot-finding with direct PreAssignmentContext checks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 loadBlocksByRoomTag() fallback: no blocks left with labRotation=null after distribution (fallback matching by teacher-only, then by roomTag)
- [ ] #2 findValidSlotForBlock() rewritten: uses PreAssignmentContext O(1) checks directly instead of RotationTestRunner.testCandidate() roundtrip
- [ ] #3 AssignmentResult carries SlotAssignments so pinTeacherDayAssignment() pins directly without re-searching
- [ ] #4 Timeslot lookup is O(1) via PreAssignmentContext map (not DB query per lookup)
- [ ] #5 Backtracking capability preserved: backtrackAssign() loop, teacher ordering, partial success, track/untrack all unchanged
- [ ] #6 All existing tests pass (mvn test)
- [ ] #7 Pre-assignment pipeline completes on dev profile: ./scripts/api/api.sh pre-assign returns COMPLETE status
- [ ] #8 No NULL labRotation warnings in logs after loadBlocksByRoomTag()
- [ ] #9 Pinned blocks have no hard constraint violations (./scripts/api/api.sh analysis)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Won't fix. The 3 failure points described were specific to 25 LabRotation entries. With only 4 T28 entries remaining after LabRotation removal (commit e71bc85), the pipeline works correctly at 100% pinning (1135/1135). Simplification is no longer needed.
<!-- SECTION:FINAL_SUMMARY:END -->
