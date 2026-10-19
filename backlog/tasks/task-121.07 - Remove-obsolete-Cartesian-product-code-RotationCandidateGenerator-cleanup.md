---
id: TASK-121.07
title: Remove obsolete Cartesian product code (RotationCandidateGenerator cleanup)
status: Done
assignee: []
created_date: '2026-02-26 09:54'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - pre-assignment
  - phase1a
  - cleanup
  - subtask
dependencies:
  - task-121.06
parent_task_id: TASK-121
priority: medium
ordinal: 120000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

After the greedy assigner is wired in and tested, the old Cartesian product infrastructure is dead code.

## Files to Delete
- `service/RotationCandidateGenerator.java` — replaced by `GreedyRotationAssigner`
- `service/RotationCandidate.java` — replaced by `TeacherDayAssignment`
- `test/RotationCandidateGeneratorTest.java` — tests for deleted class

## Files to Update
- Remove any remaining references to `RotationCandidateGenerator` or `RotationCandidate`
- Update `HybridPhase1aService` if any residual imports remain
- Update `RotationTestRunner` if it still references `RotationCandidate` types (BlockKey may need to move to its own file or to a shared location)

## Considerations
- Verify `RotationCandidate.BlockKey` is either moved or inlined where needed before deleting the parent record
- Keep `planner.phase1a.max-candidates` config property if it's repurposed (e.g., max backtrack depth), otherwise remove
- Update task-120 notes to indicate superseded by task-121

## Dependencies
- task-121.6 (all tests must pass before removing old code)

## Verification
```bash
mvn compile -DskipTests  # No compilation errors
mvn test                  # All tests pass
git diff --stat           # Verify only expected files changed
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 RotationCandidateGenerator.java deleted
- [ ] #2 RotationCandidate.java deleted (BlockKey moved if still needed)
- [ ] #3 RotationCandidateGeneratorTest.java deleted
- [ ] #4 No compilation errors
- [ ] #5 All remaining tests pass
- [ ] #6 No dead imports or references to deleted classes
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Notes

Completed 2026-02-26. 

### What Was Done
1. **BlockKey migration**: Moved `BlockKey` from `RotationCandidate` to `RotationTestRunner` as a nested record
2. **RotationCandidate updated**: Now uses `RotationTestRunner.BlockKey` instead of its own nested record
3. **Deleted files**: 
   - `RotationCandidateGenerator.java` (replaced by `GreedyRotationAssigner`)
   - `RotationCandidateGeneratorTest.java`
4. **Updated files**:
   - `HybridPhase1aService.java` - Removed `RotationCandidateGenerator` injection, updated `BlockKey` references
   - `GreedyRotationAssigner.java` - Updated to use `RotationTestRunner.BlockKey`, removed `RotationCandidateGenerator` dependency
   - `RotationTestRunner.java` - Added `BlockKey` nested record
   - Test files updated to use new `BlockKey` location

### Why RotationCandidate Was NOT Deleted
`RotationCandidate` is still used as a data transfer object for testing rotation candidates via `RotationTestRunner.testCandidate()`. The `GreedyRotationAssigner` creates temporary `RotationCandidate` objects to test day assignments. The key change was removing the Cartesian product *generator* (`RotationCandidateGenerator`), not the candidate DTO itself.

### Test Results
All 118 tests pass.
<!-- SECTION:NOTES:END -->
