---
id: TASK-038.01
title: Pre-Assignment Conflict Validation (shadow-lab-blocks)
status: Done
assignee: []
created_date: '2026-02-20 13:15'
updated_date: '2026-03-09 19:50'
labels:
  - claude-planned
  - solver
dependencies:
  - task-038
parent_task_id: TASK-038
priority: medium
ordinal: 43000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
# Pre-Assignment Conflict Validation (shadow-lab-blocks)

## Scope

**Target Branch:** `feature/shadow-lab-blocks`

**Related Tasks:** 
- task-028: Block Entity implementation (DONE)
- task-038: cartesian-shadow-lab-blocks implementation (PRIMARY - do this first)
- This task: Cherry-pick from task-038

---

## Executive Summary

Cherry-pick the pre-assignment conflict validation work from `feature/cartesian-shadow-lab-blocks` (task-038) to this branch. Both Shadow branches share the same architecture, so the changes should apply cleanly.

---

## Implementation Plan

### Step 1: Wait for task-038 Completion
Task-038 must be complete and verified before this task can begin.

### Step 2: Cherry-pick All Commits
From `feature/cartesian-shadow-lab-blocks`:
```bash
git checkout feature/shadow-lab-blocks
git cherry-pick <commits-from-task-038>
```

Files to expect:
- `DataGenerator.java`
- `PreAssignmentStatus.java`
- `PreAssignmentValidator.java`
- `AcconciaturaPreAssigner.java`
- `LabBlockPreAssigner.java`
- `ProfessionalizzantiPreAssigner.java`
- `PreAssignmentService.java`
- `application.properties`
- `SolverControls.tsx`
- `domain.ts`

### Step 3: Resolve Conflicts (if any)
The only expected conflict might be in `solverConfig.xml` (different between the two Shadow branches). Architecture-specific files should be identical.

### Step 4: Verify on shadow-lab-blocks
```bash
./scripts/rebuild-ui.sh
./scripts/api/api.sh pre-assign
./scripts/api/api.sh analysis -j | jq '.hardScore'
```

Expected: Same results as cartesian-shadow-lab-blocks (hard score close to 0).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All commits from task-038 cherry-picked successfully
- [ ] #2 Pre-assignment produces zero hard constraint violations (Hard score = 0 before solving)
- [ ] #3 API response includes conflict-free validation status
- [ ] #4 No merge conflicts (or all conflicts resolved)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
### Step 1: Wait for task-038 Completion
Task-038 must be complete and verified before this task can begin.

### Step 2: Cherry-pick All Commits
From `feature/cartesian-shadow-lab-blocks`:
```bash
git checkout feature/shadow-lab-blocks
git cherry-pick <commits-from-task-038>
```

Files to expect:
- `DataGenerator.java`
- `PreAssignmentStatus.java`
- `PreAssignmentValidator.java`
- `AcconciaturaPreAssigner.java`
- `LabBlockPreAssigner.java`
- `ProfessionalizzantiPreAssigner.java`
- `PreAssignmentService.java`
- `application.properties`
- `SolverControls.tsx`
- `domain.ts`

### Step 3: Resolve Conflicts (if any)
The only expected conflict might be in `solverConfig.xml` (different between the two Shadow branches). Architecture-specific files should be identical.

### Step 4: Verify on shadow-lab-blocks
```bash
./scripts/rebuild-ui.sh
./scripts/api/api.sh pre-assign
./scripts/api/api.sh analysis -j | jq '.hardScore'
```

Expected: Same results as cartesian-shadow-lab-blocks (hard score close to 0).
<!-- SECTION:DESCRIPTION:END -->
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **1/5** (Low)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 1 | Cherry-pick commits |
| Risk | 1 | Low risk, same code |
| Testing | 1 | Verify on shadow-lab-blocks |
| Dependencies | 1 | Single dependency |
| **Total** | **1** | Quick task (1-2 hours) |

---

## Priority: **MEDIUM**

Simple cherry-pick from task-038. Low effort but depends on task-038 completion.

Archived: Superseded by HybridPhase1aService (task-121). Parent task task-038 was also archived as the CSP approach replaces the original conflict validation design.
<!-- SECTION:NOTES:END -->
