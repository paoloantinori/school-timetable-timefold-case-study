---
id: task-038
title: Pre-Assignment Conflict Validation (cartesian-shadow-lab-blocks)
status: Done
assignee: []
created_date: '2026-02-19 11:10'
updated_date: '2026-02-20 13:14'
labels:
  - claude-planned
  - solver
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
# Pre-Assignment Conflict Validation (cartesian-shadow-lab-blocks)

## Scope

**Target Branch:** `feature/cartesian-shadow-lab-blocks`

**Related Tasks:** 
- task-028: Block Entity implementation (DONE)
- task-047: Second shadow branch (`feature/shadow-lab-blocks`) - cherry-pick from this task

---

## Executive Summary

Propagate the pre-assignment conflict validation work from `feature/cartesian-block-entity` (task-028) to the Shadow branch family. This ensures pre-assignment produces conflict-free pinned entities before the solver runs.

---

## Architectural Differences from Block Entity

| Aspect | Block Entity (task-028) | Shadow (this task) |
|--------|-------------------------|-------------------|
| Planning Entity | `LabBlock` | `Lesson` (leader) |
| PreAssignmentValidator input | `LabBlock` or `Lesson` | `Lesson` only |
| Conflict query targets | `LabBlock` + `Lesson` | `Lesson` only |
| AcconciaturaPreAssigner creates | `LabBlock` | `Lesson` |

**Key insight:** In Shadow branches, `LabFollower` is NOT a genuine planning entity — it's a shadow variable that automatically follows its leader `Lesson`. Pre-assigners pin `Lesson` entities (the leaders), and `LabFollower` entities follow automatically.

---

## Files Summary

| File | Action | Notes |
|------|--------|-------|
| `DataGenerator.java` | Cherry-pick | Architecture-agnostic seed data |
| `PreAssignmentStatus.java` | Cherry-pick | New enum |
| `application.properties` | Cherry-pick | Config |
| `SolverControls.tsx` | Cherry-pick | Frontend |
| `domain.ts` | Cherry-pick | Frontend types |
| `PreAssignmentValidator.java` | **CREATE** | Shadow-specific (Lesson-based) |
| `AcconciaturaPreAssigner.java` | **ADAPT** | Add validator, keep Lesson creation |
| `LabBlockPreAssigner.java` | **ADAPT** | Add validator, keep Lesson creation |
| `PreAssignmentService.java` | **ADAPT** | Add conflict-free reporting |

---

## Implementation Plan

### Step 1: Cherry-pick Shared Files
Cherry-pick from `feature/cartesian-block-entity`:
- `DataGenerator.java` - rotation seed data
- `PreAssignmentStatus.java` - enum for status tracking
- `application.properties` - config changes
- `src/main/webui/src/components/SolverControls.tsx` - UI
- `src/main/webui/src/types/domain.ts` - types

### Step 2: Create PreAssignmentValidator for Shadow
Create new `service/PreAssignmentValidator.java`:
- `hasTeacherConflict(Teacher, Timeslot)` - check Lesson table only
- `hasRoomConflict(Room, Timeslot)` - check Lesson table only
- `hasStudentGroupConflict(StudentGroup, Timeslot)` - check Lesson table only
- `canPin(Lesson, Timeslot, Room)` - combined validation

**Note:** No `canPin(LabBlock)` needed — Shadow doesn't have LabBlock as planning entity.

### Step 3: Adapt AcconciaturaPreAssigner
- Add teacher filter (T28=Thu, T26=Wed, T18=Tue)
- Add PreAssignmentValidator integration
- Keep Lesson creation (don't change to LabBlock)
- Before pinning, call `validator.canPin()`

### Step 4: Adapt LabBlockPreAssigner
- Add PreAssignmentValidator integration
- Keep Lesson creation
- Before pinning, call `validator.canPin()`

### Step 5: Adapt ProfessionalizzantiPreAssigner
- Add teacher conflict checking
- Before pinning, call `validator.canPin()`

### Step 6: Update PreAssignmentService
Add conflict-free validation reporting to API response.

### Step 7: Verify on cartesian-shadow-lab-blocks
```bash
./scripts/rebuild-ui.sh
./scripts/api/api.sh pre-assign
./scripts/api/api.sh analysis -j | jq '.hardScore'
```

Expected: Hard score close to 0 (only acceptable violations like H15, doppio).

---

## Verification

1. Build and start: `./scripts/rebuild-ui.sh && ./planner.sh wait`
2. Run pre-assignment: `./scripts/api/api.sh pre-assign`
3. Check hard score: `./scripts/api/api.sh analysis -j | jq '.hardScore'`
4. Expected: H1/H2/H3 conflicts = 0, H5 violations = 0 (T28 only on Thu)

**Success criteria:** Pre-assignment produces conflict-free pinned entities with hard score dominated only by H15 (structural) and doppio conflicts.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 **Given** a PreAssignmentValidator class, **When** checking for teacher/room/student conflicts, **Then** it queries only Lesson entities (not LabFollower) and returns true if a conflict exists
- [ ] #2 **Given** AcconciaturaPreAssigner about to pin a Lesson, **When** validator.canPin() returns false, **Then** the Lesson is NOT pinned and a warning is logged
- [ ] #3 **Given** LabBlockPreAssigner about to pin consecutive Lessons, **When** any slot has a conflict, **Then** that slot is skipped (other slots may still be pinned)
- [ ] #4 **Given** ProfessionalizzantiPreAssigner about to pin a Lesson, **When** validator.canPin() returns false, **Then** the Lesson is NOT pinned and a warning is logged
- [ ] #5 **Given** a completed pre-assignment run, **When** the API response is returned, **Then** it includes { "conflictFree": true/false, "violations": [...] }

- [ ] #6 **Given** a fresh profile with pre-assignment completed, **When** /api/schedule/analysis is called, **Then** hard score = 0 (no hard constraint violations)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
### Step 1: Create PreAssignmentValidator
Create new `service/PreAssignmentValidator.java` with methods:
- `hasTeacherConflict(Teacher teacher, Timeslot ts)` - check Lesson only (no LabBlock queries)
- `hasRoomConflict(Room room, Timeslot ts)` - check Lesson only
- `hasStudentGroupConflict(StudentGroup group, Timeslot ts)` - check Lesson only
- `canPin(Lesson lesson, Timeslot ts, Room room)` - combined validation

**Note:** No `canPin(LabBlock)` overload needed — Shadow doesn't have LabBlock as a planning entity.

### Step 2: Integrate into AcconciaturaPreAssigner
- Before pinning each Lesson (leader), call `validator.canPin()`
- If conflict, log warning and skip assignment
- Track per-teacher and per-room usage during the run (in-memory Map)

### Step 3: Integrate into LabBlockPreAssigner
- Extend conflict checking to include teacher conflicts
- Pre-assigner pins consecutive Lessons, check each one
- Before pinning, call `validator.canPin()`

### Step 4: Integrate into ProfessionalizzantiPreAssigner
- Same as Block Entity - add teacher tracking
- Before pinning, call `validator.canPin()`

### Step 5: Add Post-Assignment Validation Report
- Same as Block Entity
- After all pre-assignment steps, query for constraint violations in pinned entities
- Return in API response: `{ "conflictFree": true/false, "violations": [...] }`

### Step 6: Verification
- Run pre-assignment on fresh profile
- Call `/api/schedule/analysis`
- Expected: **Hard score = 0** before solving

---

## Files to Modify

| File | Change |
|------|--------|
| `service/PreAssignmentValidator.java` | NEW - constraint validation (Lesson queries only) |
| `service/AcconciaturaPreAssigner.java` | Add validator calls before pinning Lessons |
| `service/LabBlockPreAssigner.java` | Add teacher conflict checking (Lesson queries) |
| `service/ProfessionalizzantiPreAssigner.java` | Add teacher conflict check |
| `service/PreAssignmentService.java` | Add post-assignment validation report |
| `rest/PreAssignmentResource.java` | Return validation status in response |

---

## Verification

After implementing:
1. Run pre-assignment on a fresh profile
2. Check `/api/schedule/analysis` for hard score
3. Expected: **Hard score = 0** before solving
4. If non-zero, pre-assignment still creating conflicts
<!-- SECTION:DESCRIPTION:END -->
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
### Cherry-pick Strategy

1. First identify commits on `feature/cartesian-block-entity` containing:
   - DataGenerator changes (rotation seed data)
   - PreAssignmentStatus enum
   - Frontend changes

2. Cherry-pick these commits to `feature/cartesian-shadow-lab-blocks`

3. Then manually create/adapt the Shadow-specific files

### Key Differences to Remember

- `AcconciaturaPreAssigner` on Shadow creates `Lesson` entities, not `LabBlock`
- PreAssignmentValidator queries `Lesson` table only (no LabBlock queries)
- LabFollower is a shadow variable - don't query it directly for conflicts

### After This Task

Once complete, cherry-pick all changes to `feature/shadow-lab-blocks` (task-047).
<!-- SECTION:NOTES:END -->

2026-10-03 triage batch: OBSOLETO/superato da eventi successivi (verdetto verifier con evidenza nel transcript; dettagli nel task-674-era batch report). Chiuso per igiene di registro.
