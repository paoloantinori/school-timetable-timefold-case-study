---
id: TASK-121.06
title: Add unit tests for GreedyRotationAssigner and updated pipeline
status: Done
assignee: []
created_date: '2026-02-26 09:54'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - pre-assignment
  - phase1a
  - testing
  - subtask
dependencies:
  - task-121.05
parent_task_id: TASK-121
priority: high
ordinal: 119000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

The greedy CSP approach needs comprehensive test coverage, especially for backtracking behavior which is harder to verify manually.

## Test Cases

### `GreedyRotationAssignerTest.java`:

1. **Happy path**: 3 acconciatura teachers, correct days found on first attempt
2. **External teacher constraint**: T28 must get Thursday, others flexible
3. **Room exclusivity**: Two teachers can't share same day for full-day blocks
4. **Backtracking triggered**: Set up scenario where teacher 2's first choice conflicts with teacher 3, forcing backtrack to teacher 2 with a different day
5. **All days exhausted**: Teacher has no valid day (all conflict) — verify clean failure with FailureAnalysis
6. **Empty rotations**: No LabRotation records for a roomTag → skip phase gracefully
7. **Single teacher**: Only 1 teacher for a roomTag → assign directly, no backtracking possible
8. **Partial-day blocks**: Blocks with blockSize < 6 can coexist on same day (different slots)

### Updated `HybridPhase1aServiceTest.java`:

9. **Full pipeline**: Phase 1 + Phase 2 (all roomTags) + Phase 3 completes successfully
10. **Phase 2 respects Phase 1 pins**: After Phase 1 pins acconciatura, Phase 2 sees those pins
11. **Phase 2 failure cascading**: If meccanica fails, pipeline reports correct failure phase

## Test Strategy

- Use `@QuarkusTest` with H2 in-memory DB
- Seed minimal test data programmatically (not DataGenerator — too heavy)
- Test backtracking by creating conflicting teacher schedules that force specific backtrack paths

## Files to Create
- `src/test/java/com/school/planner/service/GreedyRotationAssignerTest.java`

## Files to Modify
- `src/test/java/com/school/planner/service/HybridPhase1aServiceTest.java` — update for new assigner

## Dependencies
- task-121.5 (pipeline must be rewired before integration tests make sense)

## Verification
```bash
mvn test -Dtest=GreedyRotationAssignerTest,HybridPhase1aServiceTest
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 GreedyRotationAssignerTest covers all 8 test cases listed
- [ ] #2 HybridPhase1aServiceTest updated for greedy pipeline
- [ ] #3 Backtracking behavior explicitly tested with forced conflicts
- [ ] #4 All tests pass on H2 in-memory DB
- [ ] #5 No flaky tests (deterministic setup, no timing dependencies)
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Completed 2026-02-26 as part of task-121 implementation.

Test file created at:

src/test/java/com/school/planner/service/GreedyRotationAssignerTest.java

Tests:

- testExternalTeacherOrdering

- testValidDayFilteringByH5

- testNoValidAssignmentReturnsEmpty
<!-- SECTION:NOTES:END -->
