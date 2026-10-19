---
id: DRAFT-003
title: >-
  Embed pre-assignment as solver custom phase (replace
  DayClusteredConstructionPhase)
status: Draft
assignee: []
created_date: '2026-03-24 11:56'
labels:
  - claude-planned
  - architecture
  - solver
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Goal
Replace the custom `DayClusteredConstructionPhase` with a `PreAssignmentPhaseCommand` that runs the equivalent of the current pre-assignment pipeline (HybridPhase1aService) as Phase 1 inside the solver.

## Motivation
- The original motivation for DayClusteredConstructionPhase (missing lessons) was caused by unassigned rooms, now fixed
- Existing benchmark (Phase1Test) is invalid because it ran WITHOUT pre-assignment — and a solution without pre-assignment is functionally incorrect
- Embedding pre-assignment in the solver pipeline means benchmarks automatically include it
- Single pipeline architecture: one REST call instead of two-step orchestration

## Scope
This is a planning/investigation task only at this stage. Do NOT implement yet.

### Investigation needed
1. **Verify pre-assignment correctness** — the pre-assignment logic is still being validated; do not embed incorrect logic into the solver
2. **Confirm CSP classes are truly CDI-independent** — GreedyRotationAssigner, AdaptiveSlotFinder, PreAssignmentContext, RotationCandidate, SlotCandidate
3. **LabRotation as problem fact** — add `List<LabRotation>` to SchoolSchedule
4. **Debugging isolation** — evaluate whether to keep the REST pre-assign endpoint as a standalone debug path
5. **Benchmark design** — create a pinned-data benchmark (following TabuVsVNDPinnedBenchmark pattern) to measure Phase 1 ON vs OFF with pre-assignment included

### Implementation (when ready)
1. Extract CSP core from HybridPhase1aService into non-CDI classes
2. Create `PreAssignmentPhaseCommand implements PhaseCommand<SchoolSchedule>`
3. Replace DayClusteredConstructionPhase in solverConfig.xml
4. Update SolverConfigParser, solverPhases.ts, docs, phase numbering
5. Run benchmark to validate

### Key files
- `src/main/java/com/school/planner/solver/DayClusteredConstructionPhase.java` (to be replaced)
- `src/main/java/com/school/planner/service/HybridPhase1aService.java` (CSP logic source)
- `src/main/java/com/school/planner/service/GreedyRotationAssigner.java` (pure Java CSP)
- `src/main/java/com/school/planner/service/AdaptiveSlotFinder.java` (pure Java CSP)
- `src/main/java/com/school/planner/service/PreAssignmentContext.java` (in-memory conflict tracker)
- `src/main/resources/solverConfig.xml` (phase config)
- `src/main/java/com/school/planner/domain/SchoolSchedule.java` (needs LabRotations as problem fact)

### Constraints / Risks
- **Do not implement until pre-assignment correctness is confirmed** — pre-assignment encapsulates domain knowledge not yet fully formalized as constraints
- **Keep REST pre-assign endpoint** as a debugging/isolation tool — it should remain functional independently
- **Benchmark with pinned data only** — any Phase 1 comparison must use pre-assigned (pinned) dataset
<!-- SECTION:DESCRIPTION:END -->
