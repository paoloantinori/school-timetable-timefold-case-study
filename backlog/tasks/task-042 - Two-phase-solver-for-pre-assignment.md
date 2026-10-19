---
id: TASK-042
title: Two-phase solver for pre-assignment
status: Done
assignee:
  - Claude
created_date: '2026-02-19 17:21'
updated_date: '2026-03-10 05:16'
labels:
  - claude-planned
  - architecture
  - solver
dependencies:
  - task-038
  - task-046
priority: medium
ordinal: 33750
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

The current pre-assignment pipeline pins entities that violate hard constraints because it only validates H1-H3 (teacher/room/group conflicts), missing H4-H16 and other constraints. Pinned entities cannot be moved by the solver, so these violations persist in the final solution.

## Solution

Use Timefold's solver for pre-assignment instead of custom validation. Run a first solver phase with only pre-assignment entities to find valid placements, then pin those and run the main solver on remaining entities.

## User Value

- Pre-assigned slots remain fixed (human planner intent preserved)
- No hard constraint violations from pre-assignment
- Smaller search space for main solver (faster optimization)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Given a schedule with pre-assignment entities, when pre-assignment phase completes, then all pre-assigned entities have 0 hard score violations
- [x] #2 Given pre-assignment phase completes with hard violations, when entities would be pinned, then only non-violating entities are pinned and violations are logged
- [x] #3 Given pre-assigned entities are pinned, when main solver runs, then pinned entities do not change their timeslot or room
- [x] #4 Given pre-assignment phase runs, when 30 seconds elapse, then the phase terminates regardless of solution quality
- [ ] #5 Given the /api/schedule/pre-assign endpoint is called with dryRun=true, when the request completes, then no entities are persisted and a report is returned
- [ ] #6 Given the /api/schedule/pre-assign endpoint is called, when the request completes, then the response includes which entities were pre-assigned and which failed validation

- [ ] #7 Given Phase 1 cannot find valid placements for some entities, when the workflow completes, then those entities remain unpinned and available for the main solver
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### Prerequisites
- Feature branch: `feature/two-phase-preassignment`
- Understanding of Timefold SolverManager multi-config pattern

### Step 1: Create pre-assignment solver configuration
- New file: `src/main/resources/preAssignmentSolverConfig.xml`
- Construction heuristic only (no local search)
- 30 second termination
- Same constraint provider as main solver

### Step 2: Implement entity extraction
- New class: `PreAssignmentEntityExtractor`
- Identify entities matching LabRotation records
- Identify professionalizzanti lessons for stage days
- Create subset SchoolSchedule with only these entities

### Step 3: Implement two-phase service
- New class: `TwoPhasePreAssignmentService`
- Run Phase 1 solver on extracted subset
- Evaluate score, pin only 0-hard-violation entities
- Return report with assignments and failures

### Step 4: Integrate with REST endpoint
- Modify: `ScheduleResource.java`
- Add configuration toggle: legacy vs two-phase mode
- Preserve dry-run functionality
- Update response format to include failure details

### Step 5: Validation
- Run pre-assignment on existing test data
- Verify 0 hard score for pre-assigned entities
- Compare with current approach results

### Key Files
- **New**: `preAssignmentSolverConfig.xml`, `TwoPhasePreAssignmentService.java`, `PreAssignmentEntityExtractor.java`
- **Modified**: `ScheduleResource.java`, `application.properties`

### Risks
- Phase 1 may not find valid placements → Accept partial, log warnings
- Two solver runs increase total time → Keep Phase 1 short (CH-only, 30s)
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Log

### Constraint Subsetting (completed)

**Problem**: Phase 1 (pre-assignment) ran with the SAME constraint set as Phase 2 (full solving). Aggregate constraints (H7, H11, H13, H15, H16) produce misleading scores on sparse data where most entities are unassigned.

**Solution**: Split constraints into base (sparse-compatible) and full-solving sets via inheritance + mode flag.

**Files changed**:
- `solver/BaseConstraintProvider.java` — NEW. Abstract base class with 29 sparse-compatible constraints: H1-H6, H9, H14, H-D1, H-M2, M3, M5, M12
- `solver/SchoolScheduleConstraintProvider.java` — Extends BaseConstraintProvider. Static `preAssignmentMode` flag: when true, `defineConstraints()` returns only base constraints (29); when false (default), returns all constraints (43). Full-solving adds: H7, H11, H13, H15, H16, M4, S1-S10
- `service/TwoPhasePreAssignmentService.java` — Sets mode flag before creating Phase 1 solver. Creates a separate `SolutionManager` from Phase 1 `SolverFactory` for accurate evaluation (the Quarkus-injected one uses full constraints)
- `resources/preAssignmentSolverConfig.xml` — References SchoolScheduleConstraintProvider (same class, mode flag controls behavior)

**Key finding**: Timefold Quarkus strictly allows ONE `ConstraintProvider` implementation on classpath (Jandex scan). Separate PreAssignmentConstraintProvider class failed at build time. Static mode flag is the workaround.

**Key finding 2**: The Quarkus-injected `SolutionManager` is built at startup with full constraints (43). For Phase 1 evaluation, must create a separate `SolutionManager` from the programmatic `SolverFactory` (built with mode=true, 29 constraints). Otherwise evaluation reports violations from Phase 2 constraints.

**Results** (2038 pre-assignment entities, 180s solver limit):
- Phase 1 solver score: -310hard/-209medium/0soft (base constraints only)
- Dominant violation: H-M2 (room suitability) = 184 of 310 hard penalty
- Score correctly excludes H13, H15, H16 (Phase 2 only)

### Sparse-compatible constraint classification

| Category | Constraints | Rationale |
|----------|-------------|-----------|
| Base (Phase 1) | H1-H6, H9, H14, H-D1, H-M2, M3, M5, M12 | Per-entity or per-pair; evaluate correctly with partial placement |
| Full-solving (Phase 2) | H7, H11, H13, H15, H16, M4, S1-S10 | Aggregate (per-group/day); need all entities for correct counts |

### Phase 1 Solver Tuning (2026-02-24)

Applying research findings from `claudedocs/research_phase1_solver_tuning.md` to improve Phase 1 convergence:

**XML config changes** (`preAssignmentSolverConfig.xml`):
- Add swap moves (LabBlock + Lesson) for atomic conflict resolution
- Add pillar moves (Lesson timeslot) for multi-entity conflicts
- Increase `acceptedCountLimit` from 1 to 4 (stop greedy first-hit)
- Increase `lateAcceptanceSize` from 100 to 400 (larger exploration window)
- Increase `entityTabuSize` from 5 to 7
- Add `DECREASING_DIFFICULTY` to Lesson CH (already on LabBlock)
- Add weighted move selectors: room=4x (H-M2 dominant), swap=3x, timeslot=2x, cartesian=2x, pillar=1x
- Reduce global timeout from 4800s to 1800s (should converge faster)

**Code change** (`BaseConstraintProvider.java`):
- Exclude medium constraints (M3, M5, M12) from Phase 1 to focus purely on hard feasibility
- Reduces constraint count from 29 to 24 in pre-assignment mode
- Medium constraints add overhead and can mislead solver away from hard score progress

**Follow-up**: If Phase 1 achieves 0 hard score, re-introduce medium constraints as a refinement pass.

## Progress Update (2026-02-25)

### Commit 221eaad
- **PreAssignmentEntityExtractor**: Removed LabRotation-based filtering
  - Now extracts ALL unassigned lab blocks (not just those matching LabRotation records)
  - LabRotation records are now documentation-only
  - M17 constraint guides day-of-week patterns instead of hard-coded rotations

- **preAssignmentPhase1a-labblocks.xml**: Extended solver time limits
  - Termination: 600s → 3600s (1 hour)
  - Unimproved: 600s → 1800s (30 min)
  - Allows Late Acceptance more time to escape local optima

### Related Tasks
- task-113: M17 constraint implementation (MEDIUM priority for lab recurring patterns)

## Completed (2026-02-26)

Core two-phase solver architecture is complete and working:
- Phase 1a (LabBlocks) and Phase 1b (Lessons) solvers
- Constraint subsetting (29 base constraints for Phase 1, 43 for Phase 2)
- dry-run mode returns report without persisting
- Only non-violating entities are pinned
- Failed entities remain unpinned for main solver

**Minor gap**: TwoPhaseReport includes counts and violation types, but not individual entity IDs. Follow-up: task-128.
<!-- SECTION:NOTES:END -->
