---
id: TASK-121.05
title: Rewire HybridPhase1aService to use GreedyRotationAssigner
status: Done
assignee: []
created_date: '2026-02-26 09:54'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - pre-assignment
  - phase1a
  - subtask
dependencies:
  - task-121.03
parent_task_id: TASK-121
priority: high
ordinal: 118000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

Replace the flat enumeration loop in `HybridPhase1aService.runPhase1()` and `runPhase2ForRoomTag()` with calls to `GreedyRotationAssigner`.

## Current Flow (to replace)

```java
// Current: generate all candidates, test each one
Stream<RotationCandidate> candidates = generator.generateCandidates(roomTag);
Iterator<RotationCandidate> it = candidates.iterator();
while (it.hasNext() && candidatesTried < maxCandidatesPerPhase) {
    RotationCandidate candidate = it.next();
    RotationTestRunner.TestResult result = testRunner.testCandidate(candidate, blocksByKeys, config);
    if (result.success()) { pinAssignments(...); return success; }
}
```

## New Flow

```java
// New: greedy per-teacher assignment with backtracking
GreedyRotationAssigner.AssignmentResult result = greedyAssigner.assign(roomTag, config);
if (result.success()) {
    int pinned = pinAssignments(result.assignments());
    return new PhaseResult(phaseName, true, result.teachersAssigned(), duration, pinned, roomTag);
} else {
    return new PhaseResult(phaseName, false, result.teachersAttempted(), duration, 0, roomTag);
}
```

## Changes Required

### `HybridPhase1aService.java`:

1. **Inject** `GreedyRotationAssigner` instead of `RotationCandidateGenerator`
2. **Replace** `runPhase1()` body — call `greedyAssigner.assign("acconciatura", config)`
3. **Replace** `runPhase2ForRoomTag()` body — call `greedyAssigner.assign(roomTag, config)`
4. **Keep** `runPhase3()` unchanged (professionalizzanti is already deterministic)
5. **Keep** `pinAssignments()` unchanged (SlotAssignment is compatible)
6. **Update** status tracking to report teachers-assigned instead of candidates-tried
7. **Keep** async execution, CompletableFuture, status polling unchanged

### `HybridPhase1aReport.PhaseResult`:

Update `candidatesEvaluated` field semantics:
- Before: "number of full candidates tested"
- After: "number of teacher-day assignments attempted" (much smaller, more meaningful)

## Files to Modify
- `service/HybridPhase1aService.java` — rewire runPhase1/runPhase2
- `service/HybridPhase1aReport.java` — optional: rename field for clarity

## Dependencies
- task-121.3 (GreedyRotationAssigner must exist)

## Verification
```bash
mvn test -Dtest=HybridPhase1aServiceTest
# Then integration test:
./scripts/start-and-wait.sh dev
./scripts/api/api.sh two-phase-hybrid
./scripts/api/api.sh score
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 HybridPhase1aService injects GreedyRotationAssigner instead of RotationCandidateGenerator
- [ ] #2 runPhase1() and runPhase2ForRoomTag() use greedy assigner
- [ ] #3 runPhase3() unchanged (professionalizzanti stays deterministic)
- [ ] #4 Async execution and status polling work as before
- [ ] #5 REST endpoint returns same response structure
- [ ] #6 Integration test: hybrid endpoint completes with 0hard score in < 10 seconds
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Completed 2026-02-26 as part of task-121 implementation.

HybridPhase1aService updated to:

- Remove RotationCandidateGenerator injection

- Use GreedyRotationAssigner.assignTeachers() for CSP backtracking

- Pin results via pinTeacherDayAssignment()
<!-- SECTION:NOTES:END -->
