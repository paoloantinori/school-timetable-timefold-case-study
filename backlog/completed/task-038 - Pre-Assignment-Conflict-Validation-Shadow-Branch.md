---
id: TASK-038
title: Pre-Assignment Conflict Validation (Shadow Branch)
status: Done
assignee: []
created_date: '2026-02-19 11:10'
updated_date: '2026-08-14 09:30'
labels:
  - claude-planned
  - solver
dependencies: []
priority: medium
ordinal: 42000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## User Value (WHY)

The pre-assignment implementation can inadvertently create hard constraint violations (H1/H2/H3 conflicts) during pre-assignment. This wastes solver time trying to fix conflicts that should never have been created.

This task adds conflict validation to the Shadow branch family's pre-assignment pipeline, ensuring that pinned Lessons never conflict with each other before the solver runs.

## Scope

**Target Branches:** 
- `feature/cartesian-shadow-lab-blocks`
- `feature/shadow-lab-blocks`

**Entity Types:** `Lesson` only (LabFollower is a shadow variable, not a genuine planning entity)

**Related Task:** task-028 covers the Block Entity branch family implementation.
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
## Complexity Score: **3/5** (Medium)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 3 | New validator class + integration |
| Risk | 2 | Well-defined scope |
| Testing | 3 | Constraint verifier tests needed |
| Dependencies | 2 | None blocking |
| **Total** | **3** | Medium effort (3-5 days) |

---

## Priority: **HIGH** (Core Correctness)

Enables conflict-free pre-assignment on Shadow branch. Prerequisite for task-038.01.

Archived: Superseded by HybridPhase1aService (task-121) which implements CSP-based backtracking that tests candidates for 0-hard violations before pinning. This new approach provides the same conflict-free guarantee with a more robust algorithm.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
OBSOLETO: il task vive sul branch morto cartesian-shadow-lab-blocks (famiglia Shadow rimossa, TASK-228/267: nessuna entita' LabFollower esiste). La validazione conflitti di pre-assignment e' in produzione su PreAssignmentValidator/HybridPhase1aService del branch attivo. Chiuso dal census backlog 2026-08-14.
<!-- SECTION:FINAL_SUMMARY:END -->
