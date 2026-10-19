---
id: TASK-231
title: Fix pre-assignment H15 validation bug - hard score should be 0
status: Done
assignee: []
created_date: '2026-03-14 19:34'
updated_date: '2026-03-14 19:41'
labels:
  - bug
  - pre-assignment
  - hard-constraints
  - H15
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
## Problem

Pre-assignment is creating **-780 hard score** from H15 violations (117 matches) when it should produce **0 hard score**. The pre-assignment pipeline should only pin lessons that result in 0 hard constraint violations.

## Root Cause

The `isValidSlot()` method in `HybridPhase1aService` (around line 1266) is **missing H15 validation** during Phase 3 (professional subjects) pre-assignment.

Current code only checks:
- H1: Teacher conflict
- H2: Room conflict  
- H3: Student group conflict
- H5: Teacher unavailability

**MISSING:** H15 (Same-day same-subject must be consecutive)

## Impact

- Phase 1 & 2 use `GreedyRotationAssigner` which properly validates H15
- Phase 3 uses `isValidSlot()` which skips H15 check
- This allows professional subject lessons to be pinned with same-subject gaps
- Result: -780 hard score (117 H15 violations) after pre-assignment

## Fix Location

**File:** `src/main/java/com/school/planner/service/HybridPhase1aService.java`

**Method:** `isValidSlot(Lesson lesson, Room room, Timeslot ts)` around line 1266

**Fix:** Add H15 validation:
```java
private boolean isValidSlot(Lesson lesson, Room room, Timeslot ts) {
    // ... existing H1, H2, H3, H5 checks ...
    
    // MISSING: Add H15 check
    if (context.hasH15Violation(lesson, ts)) {
        return false;
    }
    
    return true;
}
```

## Verification Steps

1. Fix `isValidSlot()` method to include H15 validation
2. Rebuild: `./scripts/core/rebuild.sh`
3. Reset schedule: `./scripts/api/api.sh reset`
4. Run pre-assignment: `./scripts/api/api.sh pre-assign`
5. Wait for completion
6. Verify hard score is **0**: `./scripts/api/api.sh analysis | jq '.hard'` should return `0`

## Expected Outcome

After fix, pre-assignment should complete with:
- Hard score: **0** (no hard violations)
- Medium score: (varies, expected -150k to -200k)
- Soft score: (varies)
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## Fix Applied (2026-03-14)

**Change:** Added H15 validation to `isValidSlot()` method in `HybridPhase1aService.java`

**Code change:**

```java

// H15: Same-day same-subject must be consecutive (no gaps)

if (context.hasH15Violation(lesson, ts)) {

return false;

}

```

**Verification results:**

- Hard score after pre-assignment: **0** ✅ (was -780 before fix)

- H15 violations: **0** ✅ (was 117 before fix)

- Pinned lessons: **3,231**

**Root cause:** Phase 3 (professional subjects) used `isValidSlot()` for direct slot assignment, which was missing H15 check. Phases 1 & 2 used `GreedyRotationAssigner` which properly validated H15 through `PreAssignmentValidator.canPinInMemory()`.
<!-- SECTION:FINAL_SUMMARY:END -->
