---
id: task-039
title: Fix pre-assign transaction timeout error
status: Done
assignee: []
created_date: '2026-02-19 17:00'
updated_date: '2026-02-21 09:58'
labels:
  - bug
  - pre-assignment
  - transaction
  - claude-planned
dependencies: []
priority: high
ordinal: 20000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
When clicking "Pre-assign all", users get a transaction rollback error:

```
Error id 4ed482f8-cc21-4515-9ae9-a49a1b4bf875-1
jakarta.transaction.RollbackException: ARJUNA016102: The transaction is not active!
```

**Root Cause**: The pre-assignment operation (acconciatura + labs + professionalizzanti) runs in a single transaction that exceeds the default timeout.

**Solution Options**:
1. Increase transaction timeout for `PreAssignmentService.runAll()` using `@TransactionConfiguration(timeout=...)` 
2. Break into multiple transactions (one per step)
3. Make async like the solver (larger refactor)

**Preferred approach**: Option 1 (quick fix) + Option 2 (break into separate transactions per step)

**Files affected**:
- `src/main/java/com/school/planner/service/PreAssignmentService.java`
- `src/main/java/com/school/planner/rest/PreAssignmentResource.java`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Given a fresh schedule with unassigned lessons, When user clicks 'Pre-assign all', Then all three steps complete without transaction rollback error
- [x] #2 Given pre-assignment is running, When any step takes up to 5 minutes, Then that step completes successfully without timeout
- [x] #3 Given pre-assignment completes, When checking server logs, Then no 'transaction is not active' errors appear
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### Root Cause
The pre-assignment runs all 3 steps in a single `@Transactional` method that exceeds the default transaction timeout.

### Solution: Break into separate transactions

Each individual step already has `@Transactional`. Ensure timeout is sufficient:

```java
@Transactional(timeout = 300) // 5 minutes per step
public StepResult runAcconciatura() { ... }

@Transactional(timeout = 300)
public StepResult runLabs() { ... }

@Transactional(timeout = 300)
public StepResult runProfessionalizzanti() { ... }
```

The `runAll()` method should NOT be `@Transactional` - it calls the transactional steps:

```java
// NOT @Transactional - each step manages its own transaction
public PreAssignmentSummary runAll() {
    var result1 = runAcconciatura();
    var result2 = runLabs();
    var result3 = runProfessionalizzanti();
    return combine(result1, result2, result3);
}
```

### Files to Modify
- `src/main/java/com/school/planner/service/PreAssignmentService.java`
  - Add `timeout = 300` to each step method
  - Remove `@Transactional` from `runAll()` if present

### Verification
1. Start app: `./planner.sh start dev && ./planner.sh wait`
2. Click "Pre-assign all"
3. Verify no transaction timeout error in logs
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Complexity Score: **1/5** (Low)

| Factor | Score | Notes |
|--------|-------|-------|
| Scope | 1 | Single file, annotation change |
| Risk | 1 | Low risk, no logic changes |
| Testing | 1 | Verify no timeout error |
| Dependencies | 1 | None |
| **Total** | **1** | Quick fix (30 min) |

---

## Priority: **HIGH** (Critical Bug)

Blocking bug - users cannot complete pre-assignment. Must be fixed before any other pre-assignment work can be tested.
<!-- SECTION:NOTES:END -->
