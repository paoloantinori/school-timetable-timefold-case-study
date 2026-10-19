---
id: task-120
title: 'Hybrid Phase 1a: Rotation Generation + Test Loop'
status: Done
assignee:
  - claude
created_date: '2026-02-25 16:28'
updated_date: '2026-02-26 09:54'
labels:
  - claude-planned
  - solver
  - pre-assignment
  - phase1a
dependencies: []
priority: high
ordinal: 5750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Context

**Problem**: Phase 1a is too slow to converge and never reaches 0hard in reasonable time.

**Root Cause**: After removing LabRotation-based filtering (which caused structural infeasibility), the solver explores ALL possible placements without guidance.

**Goal**: Implement a generate → test → abort → retry loop that mimics human planner behavior, respecting the sequential phases of the pre-assignment pipeline.

## Human Planner's Sequential Phases

| Phase | Name | Description |
|-------|------|-------------|
| **1** | Pin Acconciatura Lab Rotation | External teachers (T28, T26, T18) with fixed days. Groups rotate in trimesters. Pinned, never moved. |
| **2** | Place Other Lab Blocks | Mechanics, fashion, IT labs with fixed weekly patterns. Each teacher has dedicated sessions. |
| **3** | Place Professional Subjects on Stage Days | Professional subjects on stage days (students on stage earn credits). Fundamentals blocked on these days. |

**Critical Rule**: Within each phase, generate + test rotations. Only proceed to next phase when current phase achieves 0hard.

## Implementation Steps

### Step 1: Create `RotationCandidateGenerator.java`
- Generate rotation candidates with varying dayOfWeek assignments
- Group blocks by (teacher, studentGroup) pairs
- Iterate through days: THU → WED → TUE → MON → FRI

### Step 2: Create `RotationTestRunner.java`
- Test a rotation candidate with early-abort on conflict
- Returns early if conflict found (abort behavior)

### Step 3: Create `HybridPhase1aService.java`
- Orchestrate the sequential phases with generate + test loops
- PHASE 1: Acconciatura → Generate + test → Pin → Proceed
- PHASE 2: Other Labs (meccanica, moda, informatica, termoidraulica)
- PHASE 3: Professionalizzanti

### Step 4: Add REST Endpoint
- `POST /api/schedule/pre-assign/two-phase/hybrid`

### Step 5: Create `FailureAnalysis.java`
- When a phase fails, provide detailed analysis of unsolvable constraints
- BLOCKING ERROR - no fallback to solver

## Files to Create
- `service/RotationCandidateGenerator.java`
- `service/RotationTestRunner.java`
- `service/HybridPhase1aService.java`
- `service/RotationCandidate.java`
- `service/HybridPhase1aReport.java`
- `service/FailureAnalysis.java`

## Files to Reuse
- `service/BacktrackingPreAssigner.java` - ConflictTracker, search logic
- `service/SlotCandidate.java` - Priority-ordered slot candidates
- `service/PreAssignmentContext.java` - O(1) conflict checking

## Expected Improvement
- Human planner: minutes (single rotation usually works)
- Pure solver: 30-60 min, may not reach 0hard
- Hybrid: 5-10 min, should reach 0hard (sequential phases surface conflicts early)

## Verification
```bash
./scripts/start-and-wait.sh dev
./scripts/api/api.sh pre-assign/two-phase/hybrid
./scripts/api/api.sh score  # Should show 0hard
```
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Phase 1 (Acconciatura) generates rotation candidates and achieves 0hard before proceeding
- [ ] #2 Phase 2 (Other Labs) respects Phase 1 pinned blocks and achieves 0hard per roomTag
- [ ] #3 Phase 3 (Professionalizzanti) respects Phase 1+2 pinned blocks and achieves 0hard
- [ ] #4 Failure analysis provides detailed conflict patterns when no candidate achieves 0hard
- [ ] #5 REST endpoint /api/schedule/pre-assign/two-phase/hybrid returns structured report
- [ ] #6 Hybrid approach completes in <10 minutes with 0hard score
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
### Step 1: Create `RotationCandidateGenerator.java`
- Generate rotation candidates with **Cartesian product** of dayOfWeek assignments
- For each unique (teacher, studentGroup) pair, determine valid days:
  - External teachers (T28): only their available day (THURSDAY)
  - Other teachers: all 5 weekdays
- Generate ALL valid combinations (not sequential iteration)
- Example: 3 T28 pairs (fixed Thu) + 3 other pairs (5 choices each) = 125 candidates
- Return candidates as iterable (lazy generation to avoid memory issues with large combinations)

## Subtask Breakdown

| Task | Title | Dependencies | Verification |
|------|-------|--------------|--------------|
| task-120.1 | Data Classes | None | `mvn compile` |
| task-120.2 | Rotation Candidate Generator | task-120.1 | `mvn test -Dtest=RotationCandidateGeneratorTest` |
| task-120.3 | Rotation Test Runner | task-120.1 | `mvn test -Dtest=RotationTestRunnerTest` |
| task-120.4 | Orchestration Service | task-120.1, task-120.2, task-120.3 | `mvn test -Dtest=HybridPhase1aServiceTest` |
| task-120.5 | REST Endpoint | task-120.4 | `mvn test -Dtest=HybridPhase1aResourceTest` + API test |

## Execution Order

```
task-120.1  →  compile OK
     ↓
task-120.2  →  test OK
     ↓
task-120.3  →  test OK (parallel with 120.2)
     ↓
task-120.4  →  test OK
     ↓
task-120.5  →  integration test OK
```

## Session 2026-02-26: Infrastructure Complete, Performance Issues

### What's Working
- ✅ REST endpoint is async (returns 202 Accepted immediately)
- ✅ Status polling works with live `candidatesTried` updates
- ✅ Phase tracking works (1-ACCONCIATURA → 2-LABS-* → 3-PROFESSIONALIZZANTI)
- ✅ Candidate generation is lazy (no memory issues with 390K combinations)

### Performance Problem
After testing 2,600+ candidates in Phase 1 (Acconciatura), still no valid candidate found. At ~60 candidates/10s, exhausting all 390,625 combinations would take ~18 hours.

### Root Cause Analysis Needed
1. **Constraint tightness**: Are the constraints too strict for the available search space?
2. **Candidate ordering**: Are we testing low-probability candidates first? Should prioritize:
   - Teachers with most availability constraints first
   - Days with most free slots first
3. **Early abort efficiency**: Is the test runner aborting early enough on conflicts?
4. **PreAssignmentContext**: Is conflict tracking O(1) or O(n)?

### Next Steps
1. Profile candidate testing to find bottleneck
2. Improve candidate ordering (most-constrained-first heuristic)
3. Add early abort metrics (how many candidates fail at first block vs last?)
4. Consider reducing search space with tighter day/slot filters
5. May need to relax constraints or add fallback strategy

## Session 2026-02-26: Analysis Complete → Superseded by task-121

### Outcome
Task-120 infrastructure (subtasks 120.01-120.05) is **complete and working**, but performance analysis revealed a fundamental search space modeling flaw:

- Decision variable is `(teacher, studentGroup)` pair → should be **teacher** (all pairs share same day)
- Search space inflation: 390,625 vs 12-25 candidates (32,552× too large)
- 99.994% of generated candidates violate the one-day-per-teacher invariant

### Resolution
**task-121** created with 7 subtasks to replace the Cartesian product enumeration with a greedy CSP backtracking approach. ~70% of task-120 code is reusable (PreAssignmentContext, RotationTestRunner slot testing, pipeline structure, async/status).

See Serena memory: `hybrid-phase1a/search-space-analysis-task120`
<!-- SECTION:NOTES:END -->
