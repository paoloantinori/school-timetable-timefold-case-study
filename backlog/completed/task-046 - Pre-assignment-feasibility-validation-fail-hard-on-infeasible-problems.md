---
id: task-046
title: Pre-assignment feasibility validation - fail hard on infeasible problems
status: Done
assignee: []
created_date: '2026-02-20 08:00'
updated_date: '2026-02-21 07:49'
labels:
  - claude-planned
  - solver
  - pre-assignment
dependencies:
  - task-038
priority: high
ordinal: 41000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
# Pre-Assignment Feasibility Validation: Fail Hard on Infeasible Problems

## Why This Task

Pre-assignment is a **deterministic computation** - it should either succeed 100% or fail with a clear error explaining why it's impossible. Currently, the system silently accepts partial success (e.g., 12 unpinned T28 blocks), which is unacceptable.

**User's principle**: "If we can't solve the deterministic sub-problem completely, raise a white flag and tell the administrator WHY it's impossible."

## Problem Statement

Current behavior:
- `AcconciaturaPreAssigner.assignGroupDay()` assigns blocks until it runs out of valid dates
- Unassigned blocks are tracked as `remaining` in `RotationResult` but execution continues
- No hard failure when `required blocks > available slots`
- Administrator is not informed that the seed data is infeasible

Example failure case:
- T28 needs 38 blocks for 4 student groups
- T28 is available only on Thursdays
- After excluding holidays and stage blackout days, only ~26 Thursday slots are available
- 12 blocks remain unassigned → should be a BLOCKING ERROR

## Dependencies

- **task-028**: Pre-assignment conflict validation (must be complete first - provides the validator foundation)

## Root Cause Resolution

After implementing this, the administrator will see that T28's hours are infeasible. Solutions:
1. Reduce T28's assigned hours (redistribute to other teachers)
2. Extend T28's rotation period
3. Confirm with school if T28 can teach on other days (unlikely - he's external)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 FeasibilityReport class exists with GroupFeasibility and DateExclusion nested classes
- [x] #2 PreAssignmentInfeasibleException is thrown when required blocks > available slots
- [x] #3 Exception contains detailed report: group name, teacher name, required blocks, available dates, exclusion reasons
- [x] #4 REST endpoint returns 400 Bad Request with structured JSON error response
- [x] #5 In-memory conflict tracker includes feasibility validation before assignment attempts
- [x] #6 Running pre-assignment with infeasible seed data returns error (not partial success)
- [ ] #7 Running pre-assignment with feasible seed data completes successfully with all blocks pinned
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### Step 1: Create FeasibilityReport Class
**File:** `src/main/java/com/school/planner/service/FeasibilityReport.java`

Data structure containing:
- `feasible`: boolean
- `step`: String (e.g., "acconciatura")
- `message`: String (summary)
- `groups`: List<GroupFeasibility>
  - `groupName`, `teacherName`, `dayOfWeek`, `period`
  - `requiredBlocks`, `availableDates`
  - `exclusions`: List<DateExclusion> (date + reason)

### Step 2: Create PreAssignmentInfeasibleException
**File:** `src/main/java/com/school/planner/exception/PreAssignmentInfeasibleException.java`

Custom exception that carries the FeasibilityReport for detailed error response.

### Step 3: Enhance AcconciaturaPreAssigner.assignGroupDay()
**File:** `src/main/java/com/school/planner/service/AcconciaturaPreAssigner.java`

Add feasibility check BEFORE assignment:
```java
int totalRequired = interleaved.size() + unassignedLessons.size();
if (totalRequired > validDates.size()) {
    FeasibilityReport report = buildInfeasibilityReport(...);
    throw new PreAssignmentInfeasibleException(
        "Insufficient available dates for " + group.name, report);
}
```

Add helper method `buildInfeasibilityReport()` that:
- Calculates which dates were excluded and WHY
- Returns structured report with diagnostic details

### Step 4: Add REST Exception Mapper
**File:** `src/main/java/com/school/planner/rest/PreAssignmentResource.java`

Add exception mapper for `PreAssignmentInfeasibleException`:
- Return 400 Bad Request
- Include structured JSON with error code and feasibility report

### Step 5: Update PreAssignmentService
**File:** `src/main/java/com/school/planner/service/PreAssignmentService.java`

Ensure exception propagates (don't catch and swallow).

## Files to Create/Modify

| File | Change |
|------|--------|
| `service/FeasibilityReport.java` | NEW - Report data structure |
| `exception/PreAssignmentInfeasibleException.java` | NEW - Custom exception |
| `service/AcconciaturaPreAssigner.java` | Add feasibility check |
| `rest/PreAssignmentResource.java` | Add exception mapper |
| `service/PreAssignmentService.java` | Propagate exception |

## Verification

1. Run pre-assignment with current seed data
2. Should return 400 with detailed report showing T28's infeasibility
3. Report includes: required blocks vs available dates, exclusion reasons
4. After fixing seed data, pre-assignment completes successfully
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Complete (2026-02-21)

**Files Created:**

- `service/FeasibilityReport.java` - Report data structure with GroupFeasibility and DateExclusion nested classes

- `exception/PreAssignmentInfeasibleException.java` - Custom exception carrying FeasibilityReport

- `rest/PreAssignmentExceptionMapper.java` - JAX-RS ExceptionMapper returning 400 with structured JSON

**Files Modified:**

- `service/AcconciaturaPreAssigner.java` - Added feasibility check in assignGroupDay() before assignment loop

**Verification:**

- Pre-assignment now fails with HTTP 400 when infeasible

- Error message: "Insufficient available dates for G06 WEDNESDAY: need 27 slots, only 15 available"

- Structured JSON response includes code, message, and full FeasibilityReport

**Remaining:**

- Criterion #7 (feasible seed data) requires fixing the seed data to reduce hours or extend rotation periods
<!-- SECTION:NOTES:END -->
