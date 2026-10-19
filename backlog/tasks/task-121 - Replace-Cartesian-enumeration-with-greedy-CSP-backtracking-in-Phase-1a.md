---
id: TASK-121
title: Replace Cartesian enumeration with greedy CSP backtracking in Phase 1a
status: Done
assignee:
  - claude
created_date: '2026-02-26 09:52'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - pre-assignment
  - phase1a
  - solver
  - csp
dependencies:
  - task-120
priority: high
ordinal: 113000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

Task-120 delivered the hybrid Phase 1a pipeline infrastructure (generate → test → pin), but performance analysis revealed a **fundamental search space modeling flaw**: the `RotationCandidateGenerator` uses `(teacher, studentGroup)` pairs as decision variables for its Cartesian product, when the correct variable is **the teacher** (all group pairs share the same day).

**See Serena memory**: `hybrid-phase1a/search-space-analysis-task120`

## Problem Quantified

For acconciatura (Phase 1):
- Current search space: **390,625 candidates** (Cartesian over 12 pairs)
- Correct search space: **25 candidates** (Cartesian over 3 teachers)
- With room exclusivity: **12 candidates**
- Inflation factor: **32,552×**

99.994% of candidates violate the one-day-per-teacher invariant (same teacher assigned to different days for different groups).

Secondary issue: per-candidate testing does N+1 `Timeslot.find()` queries in inner loops (~362 queries per candidate).

## Solution

Replace the flat Cartesian product enumeration with a **CSP backtracking search** that:
1. Groups BlockKeys by teacher (not pairs)
2. Orders teachers by constraint tightness (external first)
3. Assigns one teacher at a time (greedy)
4. Backtracks when a dead end is reached
5. Prunes impossible days via room exclusivity

## What to Reuse (from task-120)
- `PreAssignmentContext` — O(1) conflict checking ✅
- `RotationTestRunner.findValidSlot()` / `checkConflicts()` — slot-level testing ✅
- `HybridPhase1aService` pipeline structure (Phase 1→2→3) ✅
- Async execution, status polling, `FailureAnalysis` ✅
- REST endpoints (task-120.5) ✅

## What to Replace
- `RotationCandidateGenerator` → `GreedyRotationAssigner`
- `RotationCandidate` → `TeacherDayAssignment`
- Flat enumeration loop in `runPhase1()` / `runPhase2ForRoomTag()`

## Expected Outcome
- Acconciatura resolution: < 100ms (was projected 18 hours)
- Full pipeline: 1-5 seconds
- Correct by construction (no structurally invalid candidates generated)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Phase 1 (Acconciatura) resolves in < 1 second with 0 hard violations
- [ ] #2 Phase 2 (other labs) resolves in < 5 seconds per roomTag
- [ ] #3 No structurally invalid candidates are ever generated (one-day-per-teacher invariant enforced)
- [ ] #4 Backtracking correctly undoes previous teacher assignment when dead end reached
- [ ] #5 All existing tests continue to pass
- [ ] #6 REST endpoint /api/schedule/pre-assign/two-phase/hybrid works unchanged
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Completion Summary (2026-02-26)

All acceptance criteria met:

- ✅ Phase 1 (Acconciatura) resolves in < 1 second

- ✅ Phase 2 (other labs) resolves in < 5 seconds per roomTag

- ✅ No structurally invalid candidates generated (one-day-per-teacher invariant enforced)

- ✅ Backtracking correctly undoes previous teacher assignment when dead end reached

- ✅ All 118 tests pass

- ✅ REST endpoint /api/schedule/pre-assign/two-phase/hybrid works unchanged

### Implementation

- `GreedyRotationAssigner` - CSP backtracking engine with teacher-centric ordering

- `TeacherDayAssignment` - Immutable teacher-to-day mapping

- `RotationTestRunner.BlockKey` - Moved from RotationCandidate

- Deleted `RotationCandidateGenerator` (replaced by GreedyRotationAssigner)
<!-- SECTION:NOTES:END -->
