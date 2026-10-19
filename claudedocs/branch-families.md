# Branch Families

This project maintains multiple parallel branches that explored different Timefold Solver architectural strategies. All branches share the same domain model, seed data, frontend, and REST API. Historically the families differed in how lab blocks were modeled as planning entities (separate `LabBlock`/`LabFollower` entities on the dormant branches); since TASK-228/267 all are Lesson-only (labs are pinned `Lesson` rows). They differ today only in solver configuration and branch naming. (Other short-lived experiment, ratification, and worktree branches exist outside this family taxonomy.)

**Active branch (1):** `feature/cartesian-block-entity` is the active development branch. All others (`feature/shadow-lab-blocks`, `feature/cartesian-shadow-lab-blocks`, and the `archive/*` set) are dormant since Feb-Mar 2026.
**Archived branches:** 8 under `archive/*` (kept for reference).

> **Reality note (TASK-322 truth pass, 2026-07-14):** this file historically described 3 active branches across Baseline / Shadow / Block "families." That predates two changes: (1) consolidation onto a single active branch, and (2) TASK-228/267 denormalized `LabBlock` into pinned `Lesson` rows (no separate planning entity). The family/branch tables below are retained as history, but the Shadow and Block branches are dormant and their split-entity model (H1.1/H1.2/H1.3, `LabFollower`, etc.) no longer applies to the active branch. The current constraint set is flat (H1/H2/H3...). See the canonical table in `CLAUDE.md`.

## Active Branch

| Branch | Last commit | Notes |
|--------|-------------|-------|
| `feature/cartesian-block-entity` | active (HEAD) | The only active branch. Flat Lesson-only (labs are pinned Lessons, no LabBlock entity). Uses the 5-phase pipeline (TASK-420) with standard change/swap/pillar moves + Tabu Search, NOT a `cartesianProductMoveSelector` (that survives only in benchmark experiment XMLs). Solver internals live in `claudedocs/solver-phases.md` and `src/main/resources/solverConfig.xml`, not duplicated here. |

## Three Architectural Families

_(Historical: the families below describe the dormant/archived branches as they were built. None applies to the active branch, which is flat Lesson-only with a 5-phase pipeline. See the Reality note above and the canonical constraint table in `CLAUDE.md`.)_

### Baseline (Lesson-only) - ARCHIVED

Planning entities: `Lesson` only. Each lesson-hour is an independent `@PlanningEntity` with `timeslot` and `room` as `@PlanningVariable`. Lab contiguity was encouraged via a soft constraint (S6, later removed project-wide by TASK-183) but not structurally enforced.

| Branch | Status | Solver Variant |
|--------|--------|---------------|
| `archive/master` | archived | Standard change/swap/pillar moves, 2-phase (Late Acceptance + Tabu, then pure Tabu) |
| `archive/feature/cartesian-move-selector` | archived | Adds `cartesianProductMoveSelector` (timeslot+room changed atomically) |
| `archive/feature/room-phase` | archived | Adds a 3rd local search phase: room-only refinement |
| `archive/feature/student-compactness` | archived | Same solver config as master; student compactness experiments |

### Block Entity (LabBlock as separate @PlanningEntity)

Planning entities: `Lesson` + `LabBlock`. A `LabBlock` represents a multi-hour lab session as a single entity with `startTimeslot` and `room` planning variables. The solver moves entire blocks atomically, structurally enforcing lab contiguity. Constraints are split into L-L, L-B, and B-B variants (e.g., H1.1, H1.2, H1.3).

Construction heuristic runs in two phases: LabBlocks first (harder to place), then Lessons.

| Branch | Status | Solver Variant |
|--------|--------|---------------|
| `feature/cartesian-block-entity` | **active** | NOTE: despite the family name, this branch is Lesson-only since TASK-228/267 removed `LabBlock`. It uses the flat Baseline constraint set and the 5-phase pipeline (not the split H1.1/H1.2/H1.3 model, and not a cartesian move selector). |
| `archive/feature/block-entity` | archived | Standard moves with entity-specific selectors |
| `archive/feature/room-phase-block-entity` | archived | Adds room-only refinement phase |

### Shadow (LabFollower shadow variables)

Planning entities: `Lesson` + `LabFollower`. Lab sessions use a leader-follower pattern: the first hour is a regular `Lesson` (the leader), and subsequent hours are `LabFollower` entities whose `timeslot` is a shadow variable automatically computed from the leader's timeslot. Only the leader is moved by the solver; followers track it. Constraints are split into L-L, L-F, and F-F variants.

| Branch | Status | Solver Variant |
|--------|--------|---------------|
| `feature/cartesian-shadow-lab-blocks` | dormant | Adds cartesian timeslot+room moves |
| `feature/shadow-lab-blocks` | dormant | Standard moves, single-entity CH |
| `archive/feature/room-phase-shadow-lab-blocks` | archived | Adds room-only refinement phase |

## Other Branches

| Branch | Status | Purpose |
|--------|--------|---------|
| `archive/experiment/room-deferred` | archived | Experiment: deferred room assignment |
| `feature/pre-assignment` | dormant remote | Pre-assignment development branch (exists as `origin/feature/pre-assignment`, last touched 2026-02-16). Not kept in sync. |

## Constraint Provider Differences

All families share the same constraint semantics; the dormant Block/Shadow families historically split some constraints by entity type (the active branch uses the flat Baseline column, verified: `BaseConstraintProvider` registers `H1 - Teacher conflict`, not H1.1/H1.2/H1.3):

- **Baseline**: Single constraint per rule (e.g., H1, H2, H3, H4)
- **Block Entity**: Split into Lesson-Lesson, Lesson-LabBlock, LabBlock-LabBlock (e.g., H1.1, H1.2, H1.3)
- **Shadow**: Split into Lesson-Lesson, Lesson-LabFollower, LabFollower-LabFollower (e.g., H1.1, H1.2, H1.3)

Block Entity and Shadow have additional constraint H9 (lab entity valid placement) that doesn't exist on baseline.

See the canonical constraint ID table in CLAUDE.md for the full mapping.
